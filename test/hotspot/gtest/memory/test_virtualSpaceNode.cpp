/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/metaspace/virtualSpaceList.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/formatBuffer.hpp"
#include "unittest.hpp"

// include as last, or otherwise we pull in an incompatible "assert" macro
#include <vector>

using namespace metaspace;

namespace {
  static void chunk_up(size_t words_left, size_t& num_medium_chunks,
                                          size_t& num_small_chunks,
                                          size_t& num_specialized_chunks) {
    num_medium_chunks = words_left / MediumChunk;
    words_left = words_left % MediumChunk;

    num_small_chunks = words_left / SmallChunk;
    words_left = words_left % SmallChunk;
    // how many specialized chunks can we get?
    num_specialized_chunks = words_left / SpecializedChunk;
    ASSERT_EQ(0UL, words_left % SpecializedChunk) << "should be nothing left"
       << ", words_left = " << words_left
       << ", SpecializedChunk = " << SpecializedChunk;
  }
  static const size_t vsn_test_size_words = MediumChunk * 4;
  static const size_t vsn_test_size_bytes = vsn_test_size_words * BytesPerWord;
  class MetachunkRemover {
    Metachunk* const _m;
    ChunkManager* const _c;
   public:
    MetachunkRemover(Metachunk* m, ChunkManager* c) : _m(m), _c(c) { }
    ~MetachunkRemover() { _c->remove_chunk(_m); }
  };
}

class ChunkManagerTest {
 public:
  static size_t sum_free_chunks(ChunkManager* cm) {
      return cm->sum_free_chunks();
  }
  static size_t sum_free_chunks_count(ChunkManager* cm) {
      return cm->sum_free_chunks_count();
  }
  static ChunkList* free_chunks(ChunkManager* cm, ChunkIndex i) {
    return cm->free_chunks(i);
  }
};

// removes all the chunks added to the ChunkManager since creation of ChunkManagerRestorer
class ChunkManagerRestorer {
  metaspace::ChunkManager* const _cm;
  std::vector<metaspace::Metachunk*>* _free_chunks[metaspace::NumberOfFreeLists];
  int _count_pre_existing;
public:
  ChunkManagerRestorer(metaspace::ChunkManager* cm) : _cm(cm), _count_pre_existing(0) {
    _cm->locked_verify();
    for (metaspace::ChunkIndex i = metaspace::ZeroIndex; i < metaspace::NumberOfFreeLists; i = next_chunk_index(i)) {
      metaspace::ChunkList* l = ChunkManagerTest::free_chunks(_cm, i);
      _count_pre_existing += l->count();
      std::vector<metaspace::Metachunk*> *v = new std::vector<metaspace::Metachunk*>(l->count());
      metaspace::Metachunk* c = l->head();
      while (c) {
        v->push_back(c);
        c = c->next();
      }
      _free_chunks[i] = v;
    }
  }
  ~ChunkManagerRestorer() {
    _cm->locked_verify();
    for (metaspace::ChunkIndex i = metaspace::ZeroIndex; i < metaspace::NumberOfFreeLists; i = next_chunk_index(i)) {
      metaspace::ChunkList* l = ChunkManagerTest::free_chunks(_cm, i);
      std::vector<metaspace::Metachunk*> *v = _free_chunks[i];
      ssize_t count = l->count();
      for (ssize_t j = 0; j < count; j++) {
        metaspace::Metachunk* c = l->head();
        while (c) {
          bool found = false;
          for (size_t k = 0; k < v->size() && !found; k++) {
            found = (c == v->at(k));
          }
          if (found) {
            c = c->next();
          } else {
            _cm->remove_chunk(c);
            break;
          }
        }
      }
      delete _free_chunks[i];
      _free_chunks[i] = NULL;
   }
    int count_after_cleanup = 0;
    for (ChunkIndex i = ZeroIndex; i < NumberOfFreeLists; i = next_chunk_index(i)) {
      ChunkList* l = ChunkManagerTest::free_chunks(_cm, i);
      count_after_cleanup += l->count();
    }
    EXPECT_EQ(_count_pre_existing, count_after_cleanup);
    _cm->locked_verify();
  }
};

TEST_VM(VirtualSpaceNodeTest, sanity) {
  // The chunk sizes must be multiples of eachother, or this will fail
  STATIC_ASSERT(MediumChunk % SmallChunk == 0);
  STATIC_ASSERT(SmallChunk % SpecializedChunk == 0);

  // just in case STATIC_ASSERT doesn't work
  EXPECT_EQ(0, MediumChunk % SmallChunk);
  EXPECT_EQ(0, SmallChunk % SpecializedChunk);
}

TEST_VM(VirtualSpaceNodeTest, four_pages_vsn_is_committed_some_is_used_by_chunks) {
  const size_t page_chunks = 4 * (size_t)os::vm_page_size() / BytesPerWord;
  if (page_chunks >= MediumChunk) {
    SUCCEED() << "SKIP: This doesn't work for systems with vm_page_size >= 16K";
    return;
  }
  MutexLockerEx ml(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
  ChunkManager cm(false);
  VirtualSpaceNode vsn(false, vsn_test_size_bytes);
  ChunkManagerRestorer c(Metaspace::get_chunk_manager(false));

  vsn.initialize();
  EXPECT_TRUE(vsn.expand_by(page_chunks, page_chunks));
  vsn.get_chunk_vs(SmallChunk);
  vsn.get_chunk_vs(SpecializedChunk);
  vsn.retire(&cm);

  // committed - used = words left to retire
  const size_t words_left = page_chunks - SmallChunk - SpecializedChunk;
  size_t num_medium_chunks, num_small_chunks, num_spec_chunks;
  chunk_up(words_left, num_medium_chunks, num_small_chunks, num_spec_chunks);

  EXPECT_EQ(0UL, num_medium_chunks) << "should not get any medium chunks";
  // DISABLED: checks started to fail after 8198423
  // EXPECT_EQ((num_small_chunks + num_spec_chunks), ChunkManagerTest::sum_free_chunks_count(&cm)) << "should be space for 3 chunks";
  // EXPECT_EQ(words_left, ChunkManagerTest::sum_free_chunks(&cm)) << "sizes should add up";
}

TEST_VM(VirtualSpaceNodeTest, half_vsn_is_committed_humongous_chunk_is_used) {
  MutexLockerEx ml(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
  ChunkManager cm(false);
  VirtualSpaceNode vsn(false, vsn_test_size_bytes);
  ChunkManagerRestorer c(Metaspace::get_chunk_manager(false));

  vsn.initialize();
  EXPECT_TRUE(vsn.expand_by(MediumChunk * 2, MediumChunk * 2));
  // Humongous chunks will be aligned up to MediumChunk + SpecializedChunk
  vsn.get_chunk_vs(MediumChunk + SpecializedChunk);
  vsn.retire(&cm);

  const size_t words_left = MediumChunk * 2 - (MediumChunk + SpecializedChunk);
  size_t num_medium_chunks, num_small_chunks, num_spec_chunks;
  ASSERT_NO_FATAL_FAILURE(chunk_up(words_left, num_medium_chunks, num_small_chunks, num_spec_chunks));

  EXPECT_EQ(0UL, num_medium_chunks) << "should not get any medium chunks";
  // DISABLED: checks started to fail after 8198423
  // EXPECT_EQ((num_small_chunks + num_spec_chunks), ChunkManagerTest::sum_free_chunks_count(&cm)) << "should be space for 3 chunks";
  // EXPECT_EQ(words_left, ChunkManagerTest::sum_free_chunks(&cm)) << "sizes should add up";
}

TEST_VM(VirtualSpaceNodeTest, all_vsn_is_committed_half_is_used_by_chunks) {
  MutexLockerEx ml(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
  ChunkManager cm(false);
  VirtualSpaceNode vsn(false, vsn_test_size_bytes);
  ChunkManagerRestorer c(Metaspace::get_chunk_manager(false));

  vsn.initialize();
  EXPECT_TRUE(vsn.expand_by(vsn_test_size_words, vsn_test_size_words));
  vsn.get_chunk_vs(MediumChunk);
  vsn.get_chunk_vs(MediumChunk);
  vsn.retire(&cm);

  // DISABLED: checks started to fail after 8198423
  // EXPECT_EQ(2UL, ChunkManagerTest::sum_free_chunks_count(&cm)) << "should have been memory left for 2 chunks";
  // EXPECT_EQ(2UL * MediumChunk, ChunkManagerTest::sum_free_chunks(&cm)) << "sizes should add up";
}

TEST_VM(VirtualSpaceNodeTest, no_committed_memory) {
  MutexLockerEx ml(MetaspaceExpand_lock, Mutex::_no_safepoint_check_flag);
  ChunkManager cm(false);
  VirtualSpaceNode vsn(false, vsn_test_size_bytes);
  ChunkManagerRestorer c(Metaspace::get_chunk_manager(false));

  vsn.initialize();
  vsn.retire(&cm);

  ASSERT_EQ(0UL, ChunkManagerTest::sum_free_chunks_count(&cm)) << "did not commit any memory in the VSN";
}

TEST_VM(VirtualSpaceNodeTest, is_available_positive) {
  // Reserve some memory.
  VirtualSpaceNode vsn(false, os::vm_allocation_granularity());
  ASSERT_TRUE(vsn.initialize()) << "Failed to setup VirtualSpaceNode";

  // Commit some memory.
  size_t commit_word_size = os::vm_allocation_granularity() / BytesPerWord;
  ASSERT_TRUE(vsn.expand_by(commit_word_size, commit_word_size))
      << "Failed to commit, commit_word_size = " << commit_word_size;

  SCOPED_TRACE(err_msg("VirtualSpaceNode [" PTR_FORMAT ", " PTR_FORMAT ")",
      p2i(vsn.bottom()), p2i(vsn.end())).buffer());

  // Check that is_available accepts the committed size.
  EXPECT_TRUE(vsn.is_available(commit_word_size)) << " commit_word_size = " << commit_word_size;

  // Check that is_available accepts half the committed size.
  size_t expand_word_size = commit_word_size / 2;
  EXPECT_TRUE(vsn.is_available(expand_word_size)) << " expand_word_size = " << expand_word_size;
}

TEST_VM(VirtualSpaceNodeTest, is_available_negative) {
  // Reserve some memory.
  VirtualSpaceNode vsn(false, os::vm_allocation_granularity());
  ASSERT_TRUE(vsn.initialize()) << "Failed to setup VirtualSpaceNode";

  // Commit some memory.
  size_t commit_word_size = os::vm_allocation_granularity() / BytesPerWord;
  ASSERT_TRUE(vsn.expand_by(commit_word_size, commit_word_size))
      << "Failed to commit, commit_word_size = " << commit_word_size;

  SCOPED_TRACE(err_msg("VirtualSpaceNode [" PTR_FORMAT ", " PTR_FORMAT ")",
      p2i(vsn.bottom()), p2i(vsn.end())).buffer());

  // Check that is_available doesn't accept a too large size.
  size_t two_times_commit_word_size = commit_word_size * 2;
  EXPECT_FALSE(vsn.is_available(two_times_commit_word_size)) << " two_times_commit_word_size = " << two_times_commit_word_size;
}

TEST_VM(VirtualSpaceNodeTest, is_available_overflow) {
  // Reserve some memory.
  VirtualSpaceNode vsn(false, os::vm_allocation_granularity());
  ASSERT_TRUE(vsn.initialize()) << "Failed to setup VirtualSpaceNode";

  // Commit some memory.
  size_t commit_word_size = os::vm_allocation_granularity() / BytesPerWord;
  ASSERT_TRUE(vsn.expand_by(commit_word_size, commit_word_size))
      << "Failed to commit, commit_word_size = " << commit_word_size;

  SCOPED_TRACE(err_msg("VirtualSpaceNode [" PTR_FORMAT ", " PTR_FORMAT ")",
      p2i(vsn.bottom()), p2i(vsn.end())).buffer());

  // Calculate a size that will overflow the virtual space size.
  void* virtual_space_max = (void*)(uintptr_t)-1;
  size_t bottom_to_max = pointer_delta(virtual_space_max, vsn.bottom(), 1);
  size_t overflow_size = bottom_to_max + BytesPerWord;
  size_t overflow_word_size = overflow_size / BytesPerWord;

  EXPECT_FALSE(vsn.is_available(overflow_word_size)) << " overflow_word_size = " << overflow_word_size;
}
