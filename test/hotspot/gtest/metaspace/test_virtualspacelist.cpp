/*
 * Copyright (c) 2023, Red Hat. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "memory/metaspace/chunklevel.hpp"
#include "memory/metaspace/metachunk.hpp"
#include "memory/metaspace/metaspaceHumongousArea.hpp"
#include "memory/metaspace/metaspaceSettings.hpp"
#include "memory/metaspace/virtualSpaceList.hpp"
#include "memory/metaspace.hpp"
#include "runtime/mutexLocker.hpp"
//#define LOG_PLEASE
#include "metaspaceGtestCommon.hpp"
#include "metaspaceGtestContexts.hpp"

using namespace metaspace::chunklevel;
using metaspace::Metachunk;
using metaspace::MetaspaceHumongousArea;
using metaspace::VirtualSpaceList;
using metaspace::Settings;

class VirtualSpaceListTester {
  VirtualSpaceList* const _l;

  // Returns total reserved space, in number of chunks
  int number_of_reserved_chunks() {
    const size_t reserved = _l->reserved_words();
    assert(is_aligned(reserved, MAX_CHUNK_WORD_SIZE), "Sanity");
    return reserved / MAX_CHUNK_WORD_SIZE;
  }

public:

  VirtualSpaceListTester(VirtualSpaceList* l) : _l(l) {}

  void allocate_root_chunk_with_test_expect_success() {
    MutexLocker fcl(Metaspace_lock, Mutex::_no_safepoint_check_flag);
    Metachunk* c = _l->allocate_root_chunk();
    ASSERT_NOT_NULL(c);
    DEBUG_ONLY(c->verify());
    ASSERT_TRUE(c->is_root_chunk());
    ASSERT_TRUE(c->is_fully_uncommitted());
    ASSERT_0(_l->committed_words());
  }

  void allocate_root_chunk_with_test_expect_failure() {
    MutexLocker fcl(Metaspace_lock, Mutex::_no_safepoint_check_flag);
    ASSERT_NULL(_l->allocate_root_chunk());
    ASSERT_0(_l->committed_words());
  }

  void allocate_humongous_area_with_test_expect_success(size_t word_size) {
    MutexLocker fcl(Metaspace_lock, Mutex::_no_safepoint_check_flag);
    MetaspaceHumongousArea area;
    ASSERT_TRUE(_l->allocate_humongous_area(word_size, &area));
    DEBUG_ONLY(area.verify(word_size, false, false);)
    ASSERT_0(_l->committed_words());
  }

  void allocate_humongous_area_with_test_expect_failure(size_t word_size) {
    MutexLocker fcl(Metaspace_lock, Mutex::_no_safepoint_check_flag);
    MetaspaceHumongousArea area;
    const int num_reserved_before = number_of_reserved_chunks();
    ASSERT_FALSE(_l->allocate_humongous_area(word_size, &area));
    // Nothing should have changed
    check_number_of_reserved_chunks(num_reserved_before);
    ASSERT_0(_l->committed_words());
  }

  void check_number_of_nodes(int num_expected) {
    ASSERT_EQ(_l->num_nodes(), num_expected);
  }

  // Checks that the total reserved space is to our expectations
  void check_number_of_reserved_chunks(int num_chunks) {
    ASSERT_EQ(_l->reserved_words(), MAX_CHUNK_WORD_SIZE * num_chunks);
  }
};

static void test_expandable_list_alloc() {
  MetaspaceGtestContext context(0, 0);
  VirtualSpaceListTester tester(&context.vslist());

  const size_t words_per_node = Settings::virtual_space_node_default_word_size();
  const int num_chunks_per_node = words_per_node / MAX_CHUNK_WORD_SIZE;
  tester.check_number_of_nodes(0);
  tester.check_number_of_reserved_chunks(0);

  // allocate a root chunk. We expect the first node to be opened.
  tester.allocate_root_chunk_with_test_expect_success();
  tester.check_number_of_nodes(1);
  tester.check_number_of_reserved_chunks(num_chunks_per_node);

  // allocate a humongous area that does not fit into the remaining node.
  tester.allocate_humongous_area_with_test_expect_success(words_per_node);
  tester.check_number_of_nodes(2);
  tester.check_number_of_reserved_chunks(num_chunks_per_node * 2);

  // allocate another root chunk. We expect it to be satisfied from the salvaged chunks from
  // the last step, so we should see no increase in numbers
  tester.allocate_root_chunk_with_test_expect_success();
  tester.check_number_of_nodes(2);
  tester.check_number_of_reserved_chunks(num_chunks_per_node * 2);

  // Finally, allocate another humongous area that is larger than the defaul node size.
  tester.allocate_humongous_area_with_test_expect_success(words_per_node + 1234);
  tester.check_number_of_nodes(3);
  tester.check_number_of_reserved_chunks((num_chunks_per_node * 3) + 1);
}

static void test_nonexpandable_list_alloc() {
  const int num_chunks = 16;
  MetaspaceGtestContext context(0, num_chunks * MAX_CHUNK_WORD_SIZE);
  VirtualSpaceListTester tester(&context.vslist());

  tester.check_number_of_nodes(1);
  tester.check_number_of_reserved_chunks(num_chunks);

  // This list just has one large node (this mimics class space). An allocation will just
  // pointer-bump the top of this node. No salvaged chunks to take into account.
  tester.allocate_root_chunk_with_test_expect_success();
  tester.check_number_of_nodes(1);
  tester.check_number_of_reserved_chunks(num_chunks);

  tester.allocate_humongous_area_with_test_expect_success(MAX_CHUNK_WORD_SIZE * 2);
  tester.check_number_of_nodes(1);
  tester.check_number_of_reserved_chunks(num_chunks);

  tester.allocate_root_chunk_with_test_expect_success();
  tester.check_number_of_nodes(1);
  tester.check_number_of_reserved_chunks(num_chunks);

  tester.allocate_humongous_area_with_test_expect_success(MAX_CHUNK_WORD_SIZE * 2);
  tester.check_number_of_nodes(1);
  tester.check_number_of_reserved_chunks(num_chunks);
}

TEST_VM(metaspace, VirtualSpaceList_expandable) { test_expandable_list_alloc(); }
TEST_VM(metaspace, VirtualSpaceList_nonexpandable) { test_nonexpandable_list_alloc(); }
