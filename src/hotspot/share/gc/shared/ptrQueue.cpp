/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/ptrQueue.hpp"

PtrQueue::PtrQueue(PtrQueueSet* qset) :
  _index(0),
  _buf(nullptr)
{}

PtrQueue::~PtrQueue() {
  assert(_buf == nullptr, "queue must be flushed before delete");
}

size_t PtrQueue::current_capacity() const {
  if (_buf == nullptr) {
    return 0;
  } else {
    return BufferNode::make_node_from_buffer(_buf)->capacity();
  }
}

PtrQueueSet::PtrQueueSet(BufferNode::Allocator* allocator) :
  _allocator(allocator)
{}

PtrQueueSet::~PtrQueueSet() {}

void PtrQueueSet::reset_queue(PtrQueue& queue) {
  queue.set_index(queue.current_capacity());
}

void PtrQueueSet::flush_queue(PtrQueue& queue) {
  void** buffer = queue.buffer();
  if (buffer != nullptr) {
    size_t index = queue.index();
    queue.set_buffer(nullptr);
    queue.set_index(0);
    BufferNode* node = BufferNode::make_node_from_buffer(buffer, index);
    if (index == node->capacity()) {
      deallocate_buffer(node);
    } else {
      enqueue_completed_buffer(node);
    }
  }
}

bool PtrQueueSet::try_enqueue(PtrQueue& queue, void* value) {
  size_t index = queue.index();
  if (index == 0) return false;
  void** buffer = queue.buffer();
  assert(buffer != nullptr, "no buffer but non-zero index");
  buffer[--index] = value;
  queue.set_index(index);
  return true;
}

void PtrQueueSet::retry_enqueue(PtrQueue& queue, void* value) {
  assert(queue.index() != 0, "precondition");
  assert(queue.buffer() != nullptr, "precondition");
  size_t index = queue.index();
  queue.buffer()[--index] = value;
  queue.set_index(index);
}

BufferNode* PtrQueueSet::exchange_buffer_with_new(PtrQueue& queue) {
  BufferNode* node = nullptr;
  void** buffer = queue.buffer();
  if (buffer != nullptr) {
    node = BufferNode::make_node_from_buffer(buffer, queue.index());
  }
  install_new_buffer(queue);
  return node;
}

void PtrQueueSet::install_new_buffer(PtrQueue& queue) {
  BufferNode* node = _allocator->allocate();
  queue.set_buffer(BufferNode::make_buffer_from_node(node));
  queue.set_index(node->capacity());
}

void** PtrQueueSet::allocate_buffer() {
  BufferNode* node = _allocator->allocate();
  return BufferNode::make_buffer_from_node(node);
}

void PtrQueueSet::deallocate_buffer(BufferNode* node) {
  _allocator->release(node);
}
