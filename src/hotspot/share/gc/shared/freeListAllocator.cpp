/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/freeListAllocator.hpp"
#include "logging/log.hpp"
#include "utilities/globalCounter.inline.hpp"

FreeListAllocator::NodeList::NodeList() :
  _head(nullptr), _tail(nullptr), _entry_count(0) {}

FreeListAllocator::NodeList::NodeList(FreeNode* head, FreeNode* tail, size_t entry_count) :
  _head(head), _tail(tail), _entry_count(entry_count)
{
  assert((_head == nullptr) == (_tail == nullptr), "invariant");
  assert((_head == nullptr) == (_entry_count == 0), "invariant");
}

FreeListAllocator::PendingList::PendingList() :
  _tail(nullptr), _head(nullptr), _count(0) {}

size_t FreeListAllocator::PendingList::add(FreeNode* node) {
  assert(node->next() == nullptr, "precondition");
  FreeNode* old_head = _head.exchange(node);
  if (old_head != nullptr) {
    node->set_next(old_head);
  } else {
    assert(_tail == nullptr, "invariant");
    _tail = node;
  }
  return _count.add_then_fetch(1u);
}

typename FreeListAllocator::NodeList FreeListAllocator::PendingList::take_all() {
  NodeList result{_head.load_relaxed(), _tail, _count.load_relaxed()};
  _head.store_relaxed(nullptr);
  _tail = nullptr;
  _count.store_relaxed(0u);
  return result;
}

size_t FreeListAllocator::PendingList::count() const {
  return _count.load_relaxed();
}

FreeListAllocator::FreeListAllocator(const char* name, FreeListConfig* config) :
  _config(config),
  _free_count(0),
  _free_list(),
  _transfer_lock(false),
  _active_pending_list(0),
  _pending_lists()
{
  strncpy(_name, name, sizeof(_name) - 1);
  _name[sizeof(_name) - 1] = '\0';
}

void FreeListAllocator::delete_list(FreeNode* list) {
  while (list != nullptr) {
    FreeNode* next = list->next();
    list->~FreeNode();
    _config->deallocate(list);
    list = next;
  }
}

FreeListAllocator::~FreeListAllocator() {
  uint index = _active_pending_list.load_relaxed();
  NodeList pending_list = _pending_lists[index].take_all();
  delete_list(pending_list._head);
  delete_list(_free_list.pop_all());
}

// Drop existing nodes and reset all counters
void FreeListAllocator::reset() {
  uint index = _active_pending_list.load_relaxed();
  _pending_lists[index].take_all();
  _free_list.pop_all();
  _free_count.store_relaxed(0u);
}

size_t FreeListAllocator::free_count() const {
  return _free_count.load_relaxed();
}

size_t FreeListAllocator::pending_count() const {
  uint index = _active_pending_list.load_relaxed();
  return _pending_lists[index].count();
}

// To solve the ABA problem, popping a node from the _free_list is performed within
// a GlobalCounter critical section, and pushing nodes onto the _free_list is done
// after a GlobalCounter synchronization associated with the nodes to be pushed.
void* FreeListAllocator::allocate() {
  FreeNode* node = nullptr;
  if (free_count() > 0) {
    // Protect against ABA; see release().
    GlobalCounter::CriticalSection cs(Thread::current());
    node = _free_list.pop();
  }

  if (node != nullptr) {
    node->~FreeNode();
    // Decrement count after getting buffer from free list.  This, along
    // with incrementing count before adding to free list, ensures count
    // never underflows.
    size_t count = _free_count.sub_then_fetch(1u);
    assert((count + 1) != 0, "_free_count underflow");
    return node;
  } else {
    return _config->allocate();
  }
}

// The release synchronizes on the critical sections before adding to
// the _free_list. But we don't want to make every release have to do a
// synchronize. Instead, we initially place released nodes on the pending list,
// and transfer them to the _free_list in batches. Only one transfer at a time is
// permitted, with a lock bit to control access to that phase. While a transfer
// is in progress, other threads might be adding other nodes to the pending list,
// to be dealt with by some later transfer.
void FreeListAllocator::release(void* free_node) {
  assert(free_node != nullptr, "precondition");
  assert(is_aligned(free_node, sizeof(FreeNode)), "Unaligned addr " PTR_FORMAT, p2i(free_node));
  FreeNode* node = ::new (free_node) FreeNode();

  // The pending list is double-buffered.  Add node to the currently active
  // pending list, within a critical section so a transfer will wait until
  // we're done with what might be the pending list to be transferred.
  {
    GlobalCounter::CriticalSection cs(Thread::current());
    uint index = _active_pending_list.load_acquire();
    size_t count = _pending_lists[index].add(node);
    if (count <= _config->transfer_threshold()) return;
  }
  // Attempt transfer when number pending exceeds the transfer threshold.
  try_transfer_pending();
}

// Try to transfer nodes from the pending list to _free_list, with a
// synchronization delay for any in-progress pops from the _free_list,
// to solve ABA there.  Return true if performed a (possibly empty)
// transfer, false if blocked from doing so by some other thread's
// in-progress transfer.
bool FreeListAllocator::try_transfer_pending() {
  // Attempt to claim the lock.
  if (_transfer_lock.load_relaxed() || // Skip CAS if likely to fail.
      _transfer_lock.compare_exchange(false, true)) {
    return false;
  }
  // Have the lock; perform the transfer.

  // Change which pending list is active.  Don't need an atomic RMW since
  // we have the lock and we're the only writer.
  uint index = _active_pending_list.load_relaxed();
  uint new_active = (index + 1) % ARRAY_SIZE(_pending_lists);
  _active_pending_list.release_store(new_active);

  // Wait for all critical sections in the buffer life-cycle to complete.
  // This includes _free_list pops and adding to the now inactive pending
  // list.
  GlobalCounter::write_synchronize();

  // Transfer the inactive pending list to _free_list.
  NodeList transfer_list = _pending_lists[index].take_all();
  size_t count = transfer_list._entry_count;
  if (count > 0) {
    // Update count first so no underflow in allocate().
    _free_count.add_then_fetch(count);
    _free_list.prepend(*transfer_list._head, *transfer_list._tail);
    log_trace(gc, freelist)
             ("Transferred %s pending to free: %zu", name(), count);
  }
  _transfer_lock.release_store(false);
  return true;
}
