/*
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

#ifndef SHARE_GC_SHARED_BUFFERNODE_HPP
#define SHARE_GC_SHARED_BUFFERNODE_HPP

#include "gc/shared/freeListAllocator.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/lockFreeStack.hpp"
#include "utilities/macros.hpp"

#include <limits>

class BufferNode {
  using InternalSizeType = LP64_ONLY(uint32_t) NOT_LP64(uint16_t);
  static_assert(sizeof(InternalSizeType) <= sizeof(size_t), "assumption");

  InternalSizeType _index;
  InternalSizeType _capacity;
  BufferNode* volatile _next;
  void* _buffer[1];             // Pseudo flexible array member.

  BufferNode(InternalSizeType capacity)
    : _index(capacity), _capacity(capacity), _next(nullptr)
  {}

  ~BufferNode() = default;

  NONCOPYABLE(BufferNode);

  static size_t buffer_offset() {
    return offset_of(BufferNode, _buffer);
  }

public:
  static constexpr size_t max_size() {
    return std::numeric_limits<InternalSizeType>::max();
  }

  static BufferNode* volatile* next_ptr(BufferNode& bn) { return &bn._next; }
  typedef LockFreeStack<BufferNode, &next_ptr> Stack;

  BufferNode* next() const     { return _next;  }
  void set_next(BufferNode* n) { _next = n;     }
  size_t index() const         { return _index; }

  void set_index(size_t i)     {
    assert(i <= capacity(), "precondition");
    _index = static_cast<InternalSizeType>(i);
  }

  size_t capacity() const      { return _capacity; }

  bool is_empty() const { return index() == capacity(); }
  size_t size() const { return capacity() - index(); }

  // Return the BufferNode containing the buffer, WITHOUT setting its index.
  static BufferNode* make_node_from_buffer(void** buffer) {
    char* base = reinterpret_cast<char*>(buffer) - buffer_offset();
    return reinterpret_cast<BufferNode*>(base);
  }

  // Return the BufferNode containing the buffer, after setting its index.
  static BufferNode* make_node_from_buffer(void** buffer, size_t index) {
    BufferNode* node = make_node_from_buffer(buffer);
    node->set_index(index);
    return node;
  }

  // Return the buffer for node.
  static void** make_buffer_from_node(BufferNode *node) {
    // &_buffer[0] might lead to index out of bounds warnings.
    return reinterpret_cast<void**>(
      reinterpret_cast<char*>(node) + buffer_offset());
  }

  class AllocatorConfig;
  class Allocator;              // Free-list based allocator.
  class TestSupport;            // Unit test support.
};

// We use BufferNode::AllocatorConfig to set the allocation options for the
// FreeListAllocator.
class BufferNode::AllocatorConfig : public FreeListConfig {
  const size_t _buffer_capacity;

public:
  explicit AllocatorConfig(size_t size);

  ~AllocatorConfig() = default;

  void* allocate() override;

  void deallocate(void* node) override;

  size_t buffer_capacity() const { return _buffer_capacity; }
};

class BufferNode::Allocator {
  friend class TestSupport;

  AllocatorConfig _config;
  FreeListAllocator _free_list;

  NONCOPYABLE(Allocator);

public:
  Allocator(const char* name, size_t buffer_capacity);
  ~Allocator() = default;

  size_t buffer_capacity() const { return _config.buffer_capacity(); }
  size_t free_count() const;
  BufferNode* allocate();
  void release(BufferNode* node);
};

#endif // SHARE_GC_SHARED_BUFFERNODE_HPP
