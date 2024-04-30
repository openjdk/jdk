/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_FREELISTALLOCATOR_HPP
#define SHARE_GC_SHARED_FREELISTALLOCATOR_HPP

#include "memory/allocation.hpp"
#include "memory/padded.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/lockFreeStack.hpp"

class FreeListConfig {
  // Desired minimum transfer batch size.  There is relatively little
  // importance to the specific number.  It shouldn't be too big, else
  // we're wasting space when the release rate is low.  If the release
  // rate is high, we might accumulate more than this before being
  // able to start a new transfer, but that's okay.
  const size_t _transfer_threshold;
protected:
  ~FreeListConfig() = default;
public:
  explicit FreeListConfig(size_t threshold = 10) : _transfer_threshold(threshold) {}

  size_t transfer_threshold() { return _transfer_threshold; }

  virtual void* allocate() = 0;

  virtual  void deallocate(void* node) = 0;
};

// Allocation is based on a lock-free list of nodes. To reduce synchronization
// overhead on the free list between allocation and release calls, the released
// nodes are first placed on a pending list, then transferred to the free list in
// batches. While on the pending list, the nodes are not available for allocation.
// The allocator uses allocation options specified by an instance of
// FreeListConfig. The FreeListConfig includes an allocation method to use in case
// the free list is empty and a deallocation method used to deallocate nodes in
// the free list. Additionally, the FreeListConfig configures the threshold used
// as a minimum batch size for transferring released nodes from the pending list
// to the free list making them available for re-allocation.
class FreeListAllocator {
  struct FreeNode {
    FreeNode* volatile _next;

    FreeNode() : _next (nullptr) { }

    FreeNode* next() { return Atomic::load(&_next); }

    FreeNode* volatile* next_addr() { return &_next; }

    void set_next(FreeNode* next) { Atomic::store(&_next, next); }
  };

  struct NodeList {
    FreeNode* _head;     // First node in list or null if empty.
    FreeNode* _tail;     // Last node in list or null if empty.
    size_t _entry_count; // Sum of entries in nodes in list.

    NodeList();

    NodeList(FreeNode* head, FreeNode* tail, size_t entry_count);
  };

  class PendingList {
    FreeNode* _tail;
    FreeNode* volatile _head;
    volatile size_t _count;

    NONCOPYABLE(PendingList);

  public:
    PendingList();
    ~PendingList() = default;

    // Add node to the list.  Returns the number of nodes in the list.
    // Thread-safe against concurrent add operations.
    size_t add(FreeNode* node);

    size_t count() const;

    // Return the nodes in the list, leaving the list empty.
    // Not thread-safe.
    NodeList take_all();
  };

  static FreeNode* volatile* next_ptr(FreeNode& node) { return node.next_addr(); }
  typedef LockFreeStack<FreeNode, &next_ptr> Stack;

  FreeListConfig* _config;
  char _name[DEFAULT_PADDING_SIZE - sizeof(FreeListConfig*)];  // Use name as padding.

#define DECLARE_PADDED_MEMBER(Id, Type, Name) \
  Type Name; DEFINE_PAD_MINUS_SIZE(Id, DEFAULT_PADDING_SIZE, sizeof(Type))
  DECLARE_PADDED_MEMBER(1, volatile size_t, _free_count);
  DECLARE_PADDED_MEMBER(2, Stack, _free_list);
  DECLARE_PADDED_MEMBER(3, volatile bool, _transfer_lock);
#undef DECLARE_PADDED_MEMBER

  volatile uint _active_pending_list;
  PendingList _pending_lists[2];

  void delete_list(FreeNode* list);

  NONCOPYABLE(FreeListAllocator);

public:
  FreeListAllocator(const char* name, FreeListConfig* config);

  const char* name() const { return _name; }

  ~FreeListAllocator();

  size_t free_count() const;
  size_t pending_count() const;

  void* allocate();
  void release(void* node);

  // Free nodes in the allocator could have been allocated out of an arena.
  // Therefore, the nodes can be freed at once when entire arena is discarded
  // without running destructors for the individual nodes. In such cases, reset
  // method should be called before the ~FreeListAllocator(). Calling the reset
  // method on nodes not managed by an arena will leak the memory by just dropping
  // the nodes to the floor.
  void reset();
  bool try_transfer_pending();

  size_t mem_size() const {
    return sizeof(*this);
  }
};

#endif // SHARE_GC_SHARED_FREELISTALLOCATOR_HPP
