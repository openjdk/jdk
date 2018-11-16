/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/ptrQueue.hpp"
#include "runtime/mutex.hpp"
#include "unittest.hpp"

// Some basic testing of BufferNode::Allocator.
TEST_VM(PtrQueueBufferAllocatorTest, test) {
  Mutex m(Mutex::leaf, "PtrQueueBufferAllocatorTest",
          false, Mutex::_safepoint_check_never);
  BufferNode::Allocator allocator(256, &m);

  // Allocate some new nodes for use in testing.
  BufferNode* nodes[10] = {};
  const size_t node_count = ARRAY_SIZE(nodes);
  for (size_t i = 0; i < node_count; ++i) {
    ASSERT_EQ(0u, allocator.free_count());
    nodes[i] = allocator.allocate();
    ASSERT_EQ(NULL, nodes[i]->next());
  }

  // Release the nodes, adding them to the allocator's free list.
  for (size_t i = 0; i < node_count; ++i) {
    ASSERT_EQ(i, allocator.free_count());
    allocator.release(nodes[i]);
    if (i == 0) {
      ASSERT_EQ(NULL, nodes[i]->next());
    } else {
      ASSERT_EQ(nodes[i - 1], nodes[i]->next());
    }
  }

  // Allocate nodes from the free list.
  for (size_t i = 0; i < node_count; ++i) {
    size_t j = node_count - i;
    ASSERT_EQ(j, allocator.free_count());
    ASSERT_EQ(nodes[j - 1], allocator.allocate());
  }
  ASSERT_EQ(0u, allocator.free_count());

  // Release nodes back to the free list.
  for (size_t i = 0; i < node_count; ++i) {
    allocator.release(nodes[i]);
  }
  ASSERT_EQ(node_count, allocator.free_count());

  // Destroy some nodes in the free list.
  // We don't have a way to verify destruction, but we can at
  // leat verify we don't crash along the way.
  allocator.reduce_free_list();
  // destroy allocator.
}
