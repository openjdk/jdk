/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, SAP.
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
#include "memory/allocation.inline.hpp"
#include "memory/metaspace.hpp"
#include "runtime/mutex.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "unittest.hpp"

#define NUM_PARALLEL_METASPACES                 50
#define MAX_PER_METASPACE_ALLOCATION_WORDSIZE   (512 * K)

//#define DEBUG_VERBOSE true

#ifdef DEBUG_VERBOSE

struct chunkmanager_statistics_t {
  int num_specialized_chunks;
  int num_small_chunks;
  int num_medium_chunks;
  int num_humongous_chunks;
};

extern void test_metaspace_retrieve_chunkmanager_statistics(Metaspace::MetadataType mdType, chunkmanager_statistics_t* out);

static void print_chunkmanager_statistics(outputStream* st, Metaspace::MetadataType mdType) {
  chunkmanager_statistics_t stat;
  test_metaspace_retrieve_chunkmanager_statistics(mdType, &stat);
  st->print_cr("free chunks: %d / %d / %d / %d", stat.num_specialized_chunks, stat.num_small_chunks,
               stat.num_medium_chunks, stat.num_humongous_chunks);
}

#endif

struct chunk_geometry_t {
  size_t specialized_chunk_word_size;
  size_t small_chunk_word_size;
  size_t medium_chunk_word_size;
};

extern void test_metaspace_retrieve_chunk_geometry(Metaspace::MetadataType mdType, chunk_geometry_t* out);


class MetaspaceAllocationTest : public ::testing::Test {
protected:

  struct {
    size_t allocated;
    Mutex* lock;
    ClassLoaderMetaspace* space;
    bool is_empty() const { return allocated == 0; }
    bool is_full() const { return allocated >= MAX_PER_METASPACE_ALLOCATION_WORDSIZE; }
  } _spaces[NUM_PARALLEL_METASPACES];

  chunk_geometry_t _chunk_geometry;

  virtual void SetUp() {
    ::memset(_spaces, 0, sizeof(_spaces));
    test_metaspace_retrieve_chunk_geometry(Metaspace::NonClassType, &_chunk_geometry);
  }

  virtual void TearDown() {
    for (int i = 0; i < NUM_PARALLEL_METASPACES; i ++) {
      if (_spaces[i].space != NULL) {
        delete _spaces[i].space;
        delete _spaces[i].lock;
      }
    }
  }

  void create_space(int i) {
    assert(i >= 0 && i < NUM_PARALLEL_METASPACES, "Sanity");
    assert(_spaces[i].space == NULL && _spaces[i].allocated == 0, "Sanity");
    if (_spaces[i].lock == NULL) {
      _spaces[i].lock = new Mutex(Monitor::native, "gtest-MetaspaceAllocationTest-lock", false, Monitor::_safepoint_check_never);
      ASSERT_TRUE(_spaces[i].lock != NULL);
    }
    // Let every ~10th space be an anonymous one to test different allocation patterns.
    const Metaspace::MetaspaceType msType = (os::random() % 100 < 10) ?
      Metaspace::AnonymousMetaspaceType : Metaspace::StandardMetaspaceType;
    {
      // Pull lock during space creation, since this is what happens in the VM too
      // (see ClassLoaderData::metaspace_non_null(), which we mimick here).
      MutexLockerEx ml(_spaces[i].lock,  Mutex::_no_safepoint_check_flag);
      _spaces[i].space = new ClassLoaderMetaspace(_spaces[i].lock, msType);
    }
    _spaces[i].allocated = 0;
    ASSERT_TRUE(_spaces[i].space != NULL);
  }

  // Returns the index of a random space where index is [0..metaspaces) and which is
  //   empty, non-empty or full.
  // Returns -1 if no matching space exists.
  enum fillgrade { fg_empty, fg_non_empty, fg_full };
  int get_random_matching_space(int metaspaces, fillgrade fg) {
    const int start_index = os::random() % metaspaces;
    int i = start_index;
    do {
      if (fg == fg_empty && _spaces[i].is_empty()) {
        return i;
      } else if ((fg == fg_full && _spaces[i].is_full()) ||
                 (fg == fg_non_empty && !_spaces[i].is_full() && !_spaces[i].is_empty())) {
        return i;
      }
      i ++;
      if (i == metaspaces) {
        i = 0;
      }
    } while (i != start_index);
    return -1;
  }

  int get_random_emtpy_space(int metaspaces) { return get_random_matching_space(metaspaces, fg_empty); }
  int get_random_non_emtpy_space(int metaspaces) { return get_random_matching_space(metaspaces, fg_non_empty); }
  int get_random_full_space(int metaspaces) { return get_random_matching_space(metaspaces, fg_full); }

  void do_test(Metaspace::MetadataType mdType, int metaspaces, int phases, int allocs_per_phase,
               float probability_for_large_allocations // 0.0-1.0
  ) {
    // Alternate between breathing in (allocating n blocks for a random Metaspace) and
    // breathing out (deleting a random Metaspace). The intent is to stress the coalescation
    // and splitting of free chunks.
    int phases_done = 0;
    bool allocating = true;
    while (phases_done < phases) {
      bool force_switch = false;
      if (allocating) {
        // Allocate space from metaspace, with a preference for completely empty spaces. This
        // should provide a good mixture of metaspaces in the virtual space.
        int index = get_random_emtpy_space(metaspaces);
        if (index == -1) {
          index = get_random_non_emtpy_space(metaspaces);
        }
        if (index == -1) {
          // All spaces are full, switch to freeing.
          force_switch = true;
        } else {
          // create space if it does not yet exist.
          if (_spaces[index].space == NULL) {
            create_space(index);
          }
          // Allocate a bunch of blocks from it. Mostly small stuff but mix in large allocations
          //  to force humongous chunk allocations.
          int allocs_done = 0;
          while (allocs_done < allocs_per_phase && !_spaces[index].is_full()) {
            size_t size = 0;
            int r = os::random() % 1000;
            if ((float)r < probability_for_large_allocations * 1000.0) {
              size = (os::random() % _chunk_geometry.medium_chunk_word_size) + _chunk_geometry.medium_chunk_word_size;
            } else {
              size = os::random() % 64;
            }
            // Note: In contrast to space creation, no need to lock here. ClassLoaderMetaspace::allocate() will lock itself.
            MetaWord* const p = _spaces[index].space->allocate(size, mdType);
            if (p == NULL) {
              // We very probably did hit the metaspace "until-gc" limit.
#ifdef DEBUG_VERBOSE
              tty->print_cr("OOM for " SIZE_FORMAT " words. ", size);
#endif
              // Just switch to deallocation and resume tests.
              force_switch = true;
              break;
            } else {
              _spaces[index].allocated += size;
              allocs_done ++;
            }
          }
        }
      } else {
        // freeing: find a metaspace and delete it, with preference for completely filled spaces.
        int index = get_random_full_space(metaspaces);
        if (index == -1) {
          index = get_random_non_emtpy_space(metaspaces);
        }
        if (index == -1) {
          force_switch = true;
        } else {
          assert(_spaces[index].space != NULL && _spaces[index].allocated > 0, "Sanity");
          // Note: do not lock here. In the "wild" (the VM), we do not so either (see ~ClassLoaderData()).
          delete _spaces[index].space;
          _spaces[index].space = NULL;
          _spaces[index].allocated = 0;
        }
      }

      if (force_switch) {
        allocating = !allocating;
      } else {
        // periodically switch between allocating and freeing, but prefer allocation because
        // we want to intermingle allocations of multiple metaspaces.
        allocating = os::random() % 5 < 4;
      }
      phases_done ++;
#ifdef DEBUG_VERBOSE
      int metaspaces_in_use = 0;
      size_t total_allocated = 0;
      for (int i = 0; i < metaspaces; i ++) {
        if (_spaces[i].allocated > 0) {
          total_allocated += _spaces[i].allocated;
          metaspaces_in_use ++;
        }
      }
      tty->print("%u:\tspaces: %d total words: " SIZE_FORMAT "\t\t\t", phases_done, metaspaces_in_use, total_allocated);
      print_chunkmanager_statistics(tty, mdType);
#endif
    }
#ifdef DEBUG_VERBOSE
    tty->print_cr("Test finished. ");
    MetaspaceUtils::print_metaspace_map(tty, mdType);
    print_chunkmanager_statistics(tty, mdType);
#endif
  }
};



TEST_F(MetaspaceAllocationTest, chunk_geometry) {
  ASSERT_GT(_chunk_geometry.specialized_chunk_word_size, (size_t) 0);
  ASSERT_GT(_chunk_geometry.small_chunk_word_size, _chunk_geometry.specialized_chunk_word_size);
  ASSERT_EQ(_chunk_geometry.small_chunk_word_size % _chunk_geometry.specialized_chunk_word_size, (size_t)0);
  ASSERT_GT(_chunk_geometry.medium_chunk_word_size, _chunk_geometry.small_chunk_word_size);
  ASSERT_EQ(_chunk_geometry.medium_chunk_word_size % _chunk_geometry.small_chunk_word_size, (size_t)0);
}


TEST_VM_F(MetaspaceAllocationTest, single_space_nonclass) {
  do_test(Metaspace::NonClassType, 1, 1000, 100, 0);
}

TEST_VM_F(MetaspaceAllocationTest, single_space_class) {
  do_test(Metaspace::ClassType, 1, 1000, 100, 0);
}

TEST_VM_F(MetaspaceAllocationTest, multi_space_nonclass) {
  do_test(Metaspace::NonClassType, NUM_PARALLEL_METASPACES, 100, 1000, 0.0);
}

TEST_VM_F(MetaspaceAllocationTest, multi_space_class) {
  do_test(Metaspace::ClassType, NUM_PARALLEL_METASPACES, 100, 1000, 0.0);
}

TEST_VM_F(MetaspaceAllocationTest, multi_space_nonclass_2) {
  // many metaspaces, with humongous chunks mixed in.
  do_test(Metaspace::NonClassType, NUM_PARALLEL_METASPACES, 100, 1000, .006f);
}

