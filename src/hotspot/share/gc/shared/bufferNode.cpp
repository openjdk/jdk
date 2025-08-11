/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/bufferNode.hpp"
#include "memory/allocation.inline.hpp"
#include "utilities/debug.hpp"

#include <new>

BufferNode::AllocatorConfig::AllocatorConfig(size_t size)
  : _buffer_capacity(size)
{
  assert(size >= 1, "Invalid buffer capacity %zu", size);
  assert(size <= max_size(), "Invalid buffer capacity %zu", size);
}

void* BufferNode::AllocatorConfig::allocate() {
  size_t byte_size = buffer_capacity() * sizeof(void*);
  return NEW_C_HEAP_ARRAY(char, buffer_offset() + byte_size, mtGC);
}

void BufferNode::AllocatorConfig::deallocate(void* node) {
  assert(node != nullptr, "precondition");
  FREE_C_HEAP_ARRAY(char, node);
}

BufferNode::Allocator::Allocator(const char* name, size_t buffer_capacity) :
  _config(buffer_capacity),
  _free_list(name, &_config)
{}

size_t BufferNode::Allocator::free_count() const {
  return _free_list.free_count();
}

BufferNode* BufferNode::Allocator::allocate() {
  auto internal_capacity = static_cast<InternalSizeType>(buffer_capacity());
  return ::new (_free_list.allocate()) BufferNode(internal_capacity);
}

void BufferNode::Allocator::release(BufferNode* node) {
  assert(node != nullptr, "precondition");
  assert(node->next() == nullptr, "precondition");
  assert(node->capacity() == buffer_capacity(),
         "Wrong size %zu, expected %zu", node->capacity(), buffer_capacity());
  node->~BufferNode();
  _free_list.release(node);
}
