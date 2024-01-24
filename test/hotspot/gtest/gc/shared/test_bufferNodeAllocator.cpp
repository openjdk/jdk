/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/bufferNode.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/semaphore.inline.hpp"
#include "runtime/thread.hpp"
#include "utilities/globalCounter.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "threadHelper.inline.hpp"
#include "unittest.hpp"

class BufferNode::TestSupport : AllStatic {
public:
  static bool try_transfer_pending(Allocator* allocator) {
    return allocator->_free_list.try_transfer_pending();
  }

  class CompletedList;
  class AllocatorThread;
  class ProcessorThread;
};

typedef BufferNode::TestSupport::CompletedList CompletedList;
typedef BufferNode::TestSupport::AllocatorThread AllocatorThread;
typedef BufferNode::TestSupport::ProcessorThread ProcessorThread;

// Some basic testing of BufferNode::Allocator.
TEST_VM(BufferNodeAllocatorTest, test) {
  const size_t buffer_capacity = 256;
  BufferNode::Allocator allocator("Test Buffer Allocator", buffer_capacity);
  ASSERT_EQ(buffer_capacity, allocator.buffer_capacity());

  // Allocate some new nodes for use in testing.
  BufferNode* nodes[10] = {};
  const size_t node_count = ARRAY_SIZE(nodes);
  for (size_t i = 0; i < node_count; ++i) {
    ASSERT_EQ(0u, allocator.free_count());
    nodes[i] = allocator.allocate();
    ASSERT_EQ((BufferNode*)NULL, nodes[i]->next());
  }

  // Release the nodes, adding them to the allocator's free list.
  for (size_t i = 0; i < node_count; ++i) {
    allocator.release(nodes[i]);
  }
  ASSERT_TRUE(BufferNode::TestSupport::try_transfer_pending(&allocator));
  ASSERT_EQ(node_count, allocator.free_count());

  // Allocate nodes from the free list.
  for (size_t i = 0; i < node_count; ++i) {
    size_t j = node_count - i;
    ASSERT_EQ(nodes[j - 1], allocator.allocate());
  }
  ASSERT_EQ(0u, allocator.free_count());

  // Release nodes back to the free list.
  for (size_t i = 0; i < node_count; ++i) {
    allocator.release(nodes[i]);
  }
  ASSERT_TRUE(BufferNode::TestSupport::try_transfer_pending(&allocator));
  ASSERT_EQ(node_count, allocator.free_count());
}

// Stress test with lock-free allocator and completed buffer list.
// Completed buffer list pop avoids ABA by also being in a critical
// section that is synchronized by the allocator's release.

class BufferNode::TestSupport::CompletedList {
  BufferNode::Stack _completed_list;

public:
  CompletedList() : _completed_list() {}

  ~CompletedList() {
    assert(_completed_list.empty(), "completed list not empty");
  }

  void push(BufferNode* node) {
    assert(node != NULL, "precondition");
    _completed_list.push(*node);
  }

  BufferNode* pop() {
    GlobalCounter::CriticalSection cs(Thread::current());
    return _completed_list.pop();
  }
};

// Simulate a mutator thread, allocating buffers and adding them to
// the completed buffer list.
class BufferNode::TestSupport::AllocatorThread : public JavaTestThread {
  BufferNode::Allocator* _allocator;
  CompletedList* _cbl;
  volatile size_t* _total_allocations;
  volatile bool* _continue_running;
  size_t _allocations;

public:
  AllocatorThread(Semaphore* post,
                  BufferNode::Allocator* allocator,
                  CompletedList* cbl,
                  volatile size_t* total_allocations,
                  volatile bool* continue_running) :
    JavaTestThread(post),
    _allocator(allocator),
    _cbl(cbl),
    _total_allocations(total_allocations),
    _continue_running(continue_running),
    _allocations(0)
  {}

  virtual void main_run() {
    while (Atomic::load_acquire(_continue_running)) {
      BufferNode* node = _allocator->allocate();
      _cbl->push(node);
      ++_allocations;
      ThreadBlockInVM tbiv(this); // Safepoint check.
    }
    tty->print_cr("allocations: " SIZE_FORMAT, _allocations);
    Atomic::add(_total_allocations, _allocations);
  }
};

// Simulate a GC thread, taking buffers from the completed buffer list
// and returning them to the allocator.
class BufferNode::TestSupport::ProcessorThread : public JavaTestThread {
  BufferNode::Allocator* _allocator;
  CompletedList* _cbl;
  volatile bool* _continue_running;

public:
  ProcessorThread(Semaphore* post,
                  BufferNode::Allocator* allocator,
                  CompletedList* cbl,
                  volatile bool* continue_running) :
    JavaTestThread(post),
    _allocator(allocator),
    _cbl(cbl),
    _continue_running(continue_running)
  {}

  virtual void main_run() {
    bool shutdown_requested = false;
    while (true) {
      BufferNode* node = _cbl->pop();
      if (node != NULL) {
        _allocator->release(node);
      } else if (shutdown_requested) {
        return;
      } else if (!Atomic::load_acquire(_continue_running)) {
        // To avoid a race that could leave buffers in the list after this
        // thread has shut down, continue processing until the list is empty
        // *after* the shut down request has been received.
        shutdown_requested = true;
      }
      ThreadBlockInVM tbiv(this); // Safepoint check.
    }
  }
};

static void run_test(BufferNode::Allocator* allocator, CompletedList* cbl) {

  // deallocation is slower than allocation, so lets create
  // more deallocation threads to prevent too large buildup of
  // free nodes (footprint)
  constexpr uint num_allocator_threads = 4;
  constexpr uint num_processor_threads = 6;
  constexpr uint milliseconds_to_run = 1000;

  Semaphore post;
  volatile size_t total_allocations = 0;
  volatile bool allocator_running = true;
  volatile bool processor_running = true;

  ProcessorThread* proc_threads[num_processor_threads] = {};
  for (uint i = 0; i < num_processor_threads; ++i) {
    proc_threads[i] = new ProcessorThread(&post,
                                          allocator,
                                          cbl,
                                          &processor_running);
    proc_threads[i]->doit();
  }

  AllocatorThread* alloc_threads[num_allocator_threads] = {};
  for (uint i = 0; i < num_allocator_threads; ++i) {
    alloc_threads[i] = new AllocatorThread(&post,
                                           allocator,
                                           cbl,
                                           &total_allocations,
                                           &allocator_running);
    alloc_threads[i]->doit();
  }

  JavaThread* this_thread = JavaThread::current();
  tty->print_cr("Stressing allocator for %u ms", milliseconds_to_run);
  {
    ThreadInVMfromNative invm(this_thread);
    this_thread->sleep(milliseconds_to_run);
  }
  Atomic::release_store(&allocator_running, false);
  for (uint i = 0; i < num_allocator_threads; ++i) {
    ThreadInVMfromNative invm(this_thread);
    post.wait_with_safepoint_check(this_thread);
  }
  Atomic::release_store(&processor_running, false);
  for (uint i = 0; i < num_processor_threads; ++i) {
    ThreadInVMfromNative invm(this_thread);
    post.wait_with_safepoint_check(this_thread);
  }
  ASSERT_TRUE(BufferNode::TestSupport::try_transfer_pending(allocator));
  tty->print_cr("total allocations: " SIZE_FORMAT, total_allocations);
  tty->print_cr("allocator free count: " SIZE_FORMAT, allocator->free_count());
}

TEST_VM(BufferNodeAllocatorTest, stress_free_list_allocator) {
  const size_t buffer_capacity = DEFAULT_PADDING_SIZE / sizeof(void*);
  BufferNode::Allocator allocator("Test Allocator", buffer_capacity);
  CompletedList completed;
  run_test(&allocator, &completed);
}
