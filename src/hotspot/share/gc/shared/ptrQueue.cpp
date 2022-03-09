/*
 * Copyright (c) 2001, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.inline.hpp"

#include <new>

PtrQueue::PtrQueue(PtrQueueSet* qset) :
  _index(0),
  _capacity_in_bytes(index_to_byte_index(qset->buffer_size())),
  _buf(NULL)
{}

PtrQueue::~PtrQueue() {
  assert(_buf == NULL, "queue must be flushed before delete");
}

BufferNode::AllocatorConfig::AllocatorConfig(size_t size) : _buffer_size(size) {}

void* BufferNode::AllocatorConfig::allocate() {
  size_t byte_size = _buffer_size * sizeof(void*);
  return NEW_C_HEAP_ARRAY(char, buffer_offset() + byte_size, mtGC);
}

void BufferNode::AllocatorConfig::deallocate(void* node) {
  assert(node != nullptr, "precondition");
  FREE_C_HEAP_ARRAY(char, node);
}

BufferNode::Allocator::Allocator(const char* name, size_t buffer_size) :
  _config(buffer_size),
  _free_list(name, &_config)
{

}

size_t BufferNode::Allocator::free_count() const {
  return _free_list.free_count();
}

BufferNode* BufferNode::Allocator::allocate() {
  return ::new (_free_list.allocate()) BufferNode();
}

void BufferNode::Allocator::release(BufferNode* node) {
  assert(node != NULL, "precondition");
  assert(node->next() == NULL, "precondition");
  node->~BufferNode();
  _free_list.release(node);
}

size_t BufferNode::Allocator::reduce_free_list(size_t remove_goal) {
  return _free_list.reduce_free_list(remove_goal);
}

PtrQueueSet::PtrQueueSet(BufferNode::Allocator* allocator) :
  _allocator(allocator)
{}

PtrQueueSet::~PtrQueueSet() {}

void PtrQueueSet::reset_queue(PtrQueue& queue) {
  if (queue.buffer() != nullptr) {
    queue.set_index(buffer_size());
  }
}

void PtrQueueSet::flush_queue(PtrQueue& queue) {
  void** buffer = queue.buffer();
  if (buffer != nullptr) {
    size_t index = queue.index();
    queue.set_buffer(nullptr);
    queue.set_index(0);
    BufferNode* node = BufferNode::make_node_from_buffer(buffer, index);
    if (index == buffer_size()) {
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
  queue.set_buffer(allocate_buffer());
  queue.set_index(buffer_size());
}

void** PtrQueueSet::allocate_buffer() {
  BufferNode* node = _allocator->allocate();
  return BufferNode::make_buffer_from_node(node);
}

void PtrQueueSet::deallocate_buffer(BufferNode* node) {
  _allocator->release(node);
}
