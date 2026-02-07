/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 */

#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/hashTable.hpp"
#include "utilities/macros.hpp"

// #define LOG_PLEASE
#include "testutils.hpp"
#include "unittest.hpp"

// Must be the same as in os::attempt_reserve_memory_between()
struct ARMB_constants {
  static constexpr uintptr_t absolute_max = NOT_LP64(G * 3) LP64_ONLY(G * 128 * 1024);
  static constexpr unsigned max_attempts = 32;
  static constexpr unsigned min_random_value_range = 16;
  static constexpr unsigned total_shuffle_threshold = 1024;
};

// Testing os::attempt_reserve_memory_between()

static void release_if_needed(char* p, size_t s) {
  if (p != nullptr) {
    os::release_memory(p, s);
  }
}

// AIX is the only platform that uses System V shm for reserving virtual memory.
// In this case, the required alignment of the allocated size (64K) and the alignment
// of possible start points of the memory region (256M) differ.
// This is not reflected by os_allocation_granularity().
// The logic here is dual to the one in pd_reserve_memory in os_aix.cpp
static size_t allocation_granularity() {
  return
    AIX_ONLY(os::vm_page_size() == 4*K ? 4*K : 256*M)
    NOT_AIX(os::vm_allocation_granularity());
}

#define ERRINFO "addr: " << ((void*)addr) << " min: " << ((void*)min) << " max: " << ((void*)max) \
                 << " bytes: " << bytes << " alignment: " << alignment << " randomized: " << randomized

static char* call_attempt_reserve_memory_between(char* min, char* max, size_t bytes, size_t alignment, bool randomized) {
  char* const  addr = os::attempt_reserve_memory_between(min, max, bytes, alignment, randomized);
  if (addr != nullptr) {
    EXPECT_TRUE(is_aligned(addr, alignment)) << ERRINFO;
    EXPECT_TRUE(is_aligned(addr, allocation_granularity())) << ERRINFO;
    EXPECT_LE(addr, max - bytes) << ERRINFO;
    EXPECT_LE(addr, (char*)ARMB_constants::absolute_max - bytes) << ERRINFO;
    EXPECT_GE(addr, min) << ERRINFO;
    EXPECT_GE(addr, (char*)os::vm_min_address()) << ERRINFO;
  }
  return addr;
}

class Expect {
  const bool _expect_success;
  const bool _expect_failure;
  const char* const _expected_result; // if _expect_success
public:
  Expect(bool expect_success, bool expect_failure, char* expected_result)
    : _expect_success(expect_success), _expect_failure(expect_failure), _expected_result(expected_result)
  {
    assert(!expect_success || !expect_failure, "make up your mind");
  }
  bool check_reality(char* result) const {
    if (_expect_failure) {
      return result == nullptr;
    }
    if (_expect_success) {
      return (_expected_result == nullptr) ? result != nullptr : result == _expected_result;
    }
    return true;
  }
  static Expect failure()           { return Expect(false, true, nullptr); }
  static Expect success_any()       { return Expect(true, false, nullptr); }
  static Expect success(char* addr) { return Expect(true, false, addr); }
  static Expect dontcare()          { return Expect(false, false, nullptr); }
};

static void test_attempt_reserve_memory_between(char* min, char* max, size_t bytes, size_t alignment, bool randomized,
                                                Expect expectation, int line = -1) {
  char* const addr = call_attempt_reserve_memory_between(min, max, bytes, alignment, randomized);
  EXPECT_TRUE(expectation.check_reality(addr)) << ERRINFO << " L" << line;
  release_if_needed(addr, bytes);
}
#undef ERRINFO

// Helper for attempt_reserve_memory_between tests to
// reserve an area with a hole in the middle
struct SpaceWithHole {
  char* _base;
  const size_t _len;
  const size_t _hole_offset;
  const size_t _hole_size;


  static constexpr size_t _p1_offset = 0;
  const size_t _p1_size;
  const size_t _p2_offset;
  const size_t _p2_size;

  char* _p1;
  char* _p2;

  size_t p1size() const { return hole_offset(); }
  size_t p2size() const { return _len - hole_size() - hole_offset(); }

public:

  char* base() const          { return _base; }
  char* end() const           { return _base + _len; }
  char* hole() const          { return _base + hole_offset(); }
  char* hole_end() const      { return hole() + hole_size(); }

  size_t hole_size() const    { return _hole_size; }
  size_t hole_offset() const  { return _hole_offset; }

  SpaceWithHole(size_t total_size, size_t hole_offset, size_t hole_size) :
    _base(nullptr), _len(total_size), _hole_offset(hole_offset), _hole_size(hole_size),
    _p1_size(hole_offset), _p2_offset(hole_offset + hole_size), _p2_size(total_size - hole_offset - hole_size),
    _p1(nullptr), _p2(nullptr)
  {
    assert(_p1_size > 0 && _p2_size > 0, "Cannot have holes at the border");
  }

  bool reserve() {
    // We cannot create a hole by punching, since NMT cannot cope with releases
    // crossing reservation boundaries. Therefore we first reserve the total,
    // release it again, reserve the parts.
    for (int i = 56; _base == nullptr && i > 32; i--) {
      // We reserve at weird outlier addresses, in order to minimize the chance of concurrent mmaps grabbing
      // the hole.
      const uintptr_t candidate = nth_bit(i);
      if ((candidate + _len) <= ARMB_constants::absolute_max) {
        _base = os::attempt_reserve_memory_at((char*)candidate, _len, mtTest);
      }
    }
    if (_base == nullptr) {
      return false;
    }
    // Release total mapping, remap the individual non-holy parts
    os::release_memory(_base, _len);
    _p1 = os::attempt_reserve_memory_at(_base + _p1_offset, _p1_size, mtTest);
    _p2 = os::attempt_reserve_memory_at(_base + _p2_offset, _p2_size, mtTest);
    if (_p1 == nullptr || _p2 == nullptr) {
      return false;
    }
    LOG_HERE("SpaceWithHole: [" PTR_FORMAT " ... [" PTR_FORMAT " ... " PTR_FORMAT ") ... " PTR_FORMAT ")",
             p2i(base()), p2i(hole()), p2i(hole_end()), p2i(end()));
    return true;
  }

  ~SpaceWithHole() {
    release_if_needed(_p1, _p1_size);
    release_if_needed(_p2, _p2_size);
  }
};

// Test that, when reserving in a range randomly, we get random results
static void test_attempt_reserve_memory_between_random_distribution(unsigned num_possible_attach_points) {

  const size_t ag = allocation_granularity();

  // Create a space that is mostly a hole bordered by two small stripes of reserved memory, with
  // as many attach points as we need.
  SpaceWithHole space((2 + num_possible_attach_points) * ag, ag, num_possible_attach_points * ag);
  if (!space.reserve()) {
    tty->print_cr("Failed to reserve holed space, skipping.");
    return;
  }

  const size_t bytes = ag;
  const size_t alignment = ag;

  // Below this threshold the API should never return memory since the randomness is too weak.
  const bool expect_failure = (num_possible_attach_points < ARMB_constants::min_random_value_range);

  // Below this threshold we expect values to be completely random, otherwise they randomized but still ordered.
  const bool total_shuffled = (num_possible_attach_points < ARMB_constants::total_shuffle_threshold);

  // Allocate n times within that hole (with subsequent deletions) and remember unique addresses returned.
  constexpr unsigned num_tries_per_attach_point = 100;
  ResourceMark rm;
  HashTable<char*, unsigned> ht;
  const unsigned num_tries = expect_failure ? 3 : (num_possible_attach_points * num_tries_per_attach_point);
  unsigned num_uniq = 0; // Number of uniq addresses returned

  // In "total shuffle" mode, all possible attach points are randomized; outside that mode, the API
  // attempts to limit fragmentation by favouring the ends of the ranges.
  const unsigned expected_variance =
    total_shuffled ? num_possible_attach_points : (num_possible_attach_points / ARMB_constants::max_attempts);

  // Its not easy to find a good threshold for automated tests to test randomness
  // that rules out intermittent errors. We apply a generous fudge factor.
  constexpr double fudge_factor = 0.25f;
  const unsigned expected_variance_with_fudge = MAX2(2u, (unsigned)((double)expected_variance * fudge_factor));

#define ERRINFO " num_possible_attach_points: " << num_possible_attach_points << " total_shuffle? " << total_shuffled \
                << " expected variance: " << expected_variance << " with fudge: " << expected_variance_with_fudge \
                << " alignment: " << alignment << " bytes: " << bytes;

  for (unsigned i = 0; i < num_tries &&
       num_uniq < expected_variance_with_fudge; // Stop early if we confirmed enough variance.
       i ++) {
    char* p = call_attempt_reserve_memory_between(space.base(), space.end(), bytes, alignment, true);
    if (p != nullptr) {
      ASSERT_GE(p, space.hole()) << ERRINFO;
      ASSERT_LE(p + bytes, space.hole_end()) << ERRINFO;
      release_if_needed(p, bytes);
      bool created = false;
      unsigned* num = ht.put_if_absent(p, 0, &created);
      (*num) ++;
      num_uniq = (unsigned)ht.number_of_entries();
    }
  }

  ASSERT_LE(num_uniq, num_possible_attach_points) << num_uniq << ERRINFO;

  if (!expect_failure) {
    ASSERT_GE(num_uniq, expected_variance_with_fudge) << ERRINFO;
  }
#undef ERRINFO
}

#define RANDOMIZED_RANGE_TEST(num) \
  TEST_VM(os, attempt_reserve_memory_between_random_distribution_ ## num ## _attach_points) { \
    test_attempt_reserve_memory_between_random_distribution(num); \
}

RANDOMIZED_RANGE_TEST(2)
RANDOMIZED_RANGE_TEST(15)
RANDOMIZED_RANGE_TEST(16)
RANDOMIZED_RANGE_TEST(712)
RANDOMIZED_RANGE_TEST(12000)

// Test that, given a smallish range - not many attach points - with a hole, we attach within that hole.
TEST_VM(os, attempt_reserve_memory_randomization_threshold) {

  constexpr int threshold = ARMB_constants::min_random_value_range;
  const size_t ps = os::vm_page_size();
  const size_t ag = allocation_granularity();

  SpaceWithHole space(ag * (threshold + 2), ag, ag * threshold);
  if (!space.reserve()) {
    tty->print_cr("Failed to reserve holed space, skipping.");
    return;
  }

  // Test with a range that only allows for (threshold - 1) reservations
  test_attempt_reserve_memory_between(space.hole(), space.hole_end() - ag, ps, ag, true, Expect::failure());

  // Test with a range just above the threshold. Should succeed.
  test_attempt_reserve_memory_between(space.hole(), space.hole_end(), ps, ag, true, Expect::success_any());
}

// Test all possible combos
TEST_VM(os, attempt_reserve_memory_between_combos) {
  const size_t large_end = NOT_LP64(G) LP64_ONLY(64 * G);
  for (size_t range_size = allocation_granularity(); range_size <= large_end; range_size *= 2) {
    for (size_t start_offset = 0; start_offset <= large_end; start_offset += (large_end / 2)) {
      char* const min = (char*)(uintptr_t)start_offset;
      char* const max = (char*)(p2u(min) + range_size);
      for (size_t bytes = os::vm_page_size(); bytes < large_end; bytes *= 2) {
        for (size_t alignment = allocation_granularity(); alignment < large_end; alignment *= 2) {
          test_attempt_reserve_memory_between(min, max, bytes, alignment, true, Expect::dontcare(), __LINE__);
          test_attempt_reserve_memory_between(min, max, bytes, alignment, false, Expect::dontcare(), __LINE__);
        }
      }
    }
  }
}

TEST_VM(os, attempt_reserve_memory_randomization_cornercases) {
  const size_t ps = os::vm_page_size();
  const size_t ag = allocation_granularity();
  constexpr size_t quarter_address_space = NOT_LP64(nth_bit(30)) LP64_ONLY(nth_bit(62));

  // Zero-sized range
  test_attempt_reserve_memory_between(nullptr, nullptr, ps, ag, false, Expect::failure());
  test_attempt_reserve_memory_between((char*)(3 * G), (char*)(3 * G), ps, ag, false, Expect::dontcare(), __LINE__);
  test_attempt_reserve_memory_between((char*)SIZE_MAX, (char*)SIZE_MAX, ps, ag, false, Expect::failure(), __LINE__);

  test_attempt_reserve_memory_between(nullptr, nullptr, ps, ag, true, Expect::failure());
  test_attempt_reserve_memory_between((char*)(3 * G), (char*)(3 * G), ps, ag, true, Expect::dontcare(), __LINE__);
  test_attempt_reserve_memory_between((char*)(3 * G), (char*)(3 * G), ps, ag, true, Expect::dontcare(), __LINE__);
  test_attempt_reserve_memory_between((char*)SIZE_MAX, (char*)SIZE_MAX, ps, ag, true, Expect::failure(), __LINE__);

  // Full size
  // Note: paradoxically, success is not guaranteed here, since a significant portion of the attach points
  // could be located in un-allocatable territory.
  test_attempt_reserve_memory_between(nullptr, (char*)SIZE_MAX, ps, quarter_address_space / 8, false, Expect::dontcare(), __LINE__);
  test_attempt_reserve_memory_between(nullptr, (char*)SIZE_MAX, ps, quarter_address_space / 8, true, Expect::dontcare(), __LINE__);

  // Very small range at start
  test_attempt_reserve_memory_between(nullptr, (char*)ag, ps, ag, false, Expect::dontcare(), __LINE__);
  test_attempt_reserve_memory_between(nullptr, (char*)ag, ps, ag, true, Expect::dontcare(), __LINE__);

  // Very small range at end
  test_attempt_reserve_memory_between((char*)(SIZE_MAX - (ag * 2)), (char*)(SIZE_MAX), ps, ag, false, Expect::dontcare(), __LINE__);
  test_attempt_reserve_memory_between((char*)(SIZE_MAX - (ag * 2)), (char*)(SIZE_MAX), ps, ag, true, Expect::dontcare(), __LINE__);

  // At start, high alignment, check if we run into neg. overflow problems
  test_attempt_reserve_memory_between(nullptr, (char*)G, ps, G, false, Expect::dontcare(), __LINE__);
  test_attempt_reserve_memory_between(nullptr, (char*)G, ps, G, true, Expect::dontcare(), __LINE__);

  // At start, very high alignment, check if we run into neg. overflow problems
  test_attempt_reserve_memory_between((char*)quarter_address_space, (char*)SIZE_MAX, ps, quarter_address_space, false, Expect::dontcare(), __LINE__);
  test_attempt_reserve_memory_between((char*)quarter_address_space, (char*)SIZE_MAX, ps, quarter_address_space, true, Expect::dontcare(), __LINE__);
}

// Test that, regardless where the hole is in the [min, max) range, if we probe nonrandomly, we will fill that hole
// as long as the range size is smaller than the number of probe attempts
// On AIX, the allocation granularity is too large and not well suited for 'small' holes, so we avoid the test
#if !defined(_AIX)
TEST_VM(os, attempt_reserve_memory_between_small_range_fill_hole) {
  const size_t ps = os::vm_page_size();
  const size_t ag = allocation_granularity();
  constexpr int num = ARMB_constants::max_attempts;
  for (int i = 0; i < num; i ++) {
    SpaceWithHole space(ag * (num + 2), ag * (i + 1), ag);
    if (!space.reserve()) {
      tty->print_cr("Failed to reserve holed space, skipping.");
    } else {
      test_attempt_reserve_memory_between(space.base() + ag, space.end() - ag, space.hole_size(), space.hole_size(), false, Expect::success(space.hole()), __LINE__);
    }
  }
}
#endif
