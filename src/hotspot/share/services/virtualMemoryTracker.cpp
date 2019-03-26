/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "logging/log.hpp"
#include "memory/metaspace.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "runtime/threadCritical.hpp"
#include "services/memTracker.hpp"
#include "services/threadStackTracker.hpp"
#include "services/virtualMemoryTracker.hpp"

size_t VirtualMemorySummary::_snapshot[CALC_OBJ_SIZE_IN_TYPE(VirtualMemorySnapshot, size_t)];

void VirtualMemorySummary::initialize() {
  assert(sizeof(_snapshot) >= sizeof(VirtualMemorySnapshot), "Sanity Check");
  // Use placement operator new to initialize static data area.
  ::new ((void*)_snapshot) VirtualMemorySnapshot();
}

void VirtualMemorySummary::snapshot(VirtualMemorySnapshot* s) {
  // Only if thread stack is backed by virtual memory
  if (ThreadStackTracker::track_as_vm()) {
    // Snapshot current thread stacks
    VirtualMemoryTracker::snapshot_thread_stacks();
    as_snapshot()->copy_to(s);
  }
}

SortedLinkedList<ReservedMemoryRegion, compare_reserved_region_base>* VirtualMemoryTracker::_reserved_regions;

int compare_committed_region(const CommittedMemoryRegion& r1, const CommittedMemoryRegion& r2) {
  return r1.compare(r2);
}

int compare_reserved_region_base(const ReservedMemoryRegion& r1, const ReservedMemoryRegion& r2) {
  return r1.compare(r2);
}

static bool is_mergeable_with(CommittedMemoryRegion* rgn, address addr, size_t size, const NativeCallStack& stack) {
  return rgn->adjacent_to(addr, size) && rgn->call_stack()->equals(stack);
}

static bool is_same_as(CommittedMemoryRegion* rgn, address addr, size_t size, const NativeCallStack& stack) {
  // It would have made sense to use rgn->equals(...), but equals returns true for overlapping regions.
  return rgn->same_region(addr, size) && rgn->call_stack()->equals(stack);
}

static LinkedListNode<CommittedMemoryRegion>* find_preceding_node_from(LinkedListNode<CommittedMemoryRegion>* from, address addr) {
  LinkedListNode<CommittedMemoryRegion>* preceding = NULL;

  for (LinkedListNode<CommittedMemoryRegion>* node = from; node != NULL; node = node->next()) {
    CommittedMemoryRegion* rgn = node->data();

    // We searched past the region start.
    if (rgn->end() > addr) {
      break;
    }

    preceding = node;
  }

  return preceding;
}

static bool try_merge_with(LinkedListNode<CommittedMemoryRegion>* node, address addr, size_t size, const NativeCallStack& stack) {
  if (node != NULL) {
    CommittedMemoryRegion* rgn = node->data();

    if (is_mergeable_with(rgn, addr, size, stack)) {
      rgn->expand_region(addr, size);
      return true;
    }
  }

  return false;
}

static bool try_merge_with(LinkedListNode<CommittedMemoryRegion>* node, LinkedListNode<CommittedMemoryRegion>* other) {
  if (other == NULL) {
    return false;
  }

  CommittedMemoryRegion* rgn = other->data();
  return try_merge_with(node, rgn->base(), rgn->size(), *rgn->call_stack());
}

bool ReservedMemoryRegion::add_committed_region(address addr, size_t size, const NativeCallStack& stack) {
  assert(addr != NULL, "Invalid address");
  assert(size > 0, "Invalid size");
  assert(contain_region(addr, size), "Not contain this region");

  // Find the region that fully precedes the [addr, addr + size) region.
  LinkedListNode<CommittedMemoryRegion>* prev = find_preceding_node_from(_committed_regions.head(), addr);
  LinkedListNode<CommittedMemoryRegion>* next = (prev != NULL ? prev->next() : _committed_regions.head());

  if (next != NULL) {
    // Ignore request if region already exists.
    if (is_same_as(next->data(), addr, size, stack)) {
      return true;
    }

    // The new region is after prev, and either overlaps with the
    // next region (and maybe more regions), or overlaps with no region.
    if (next->data()->overlap_region(addr, size)) {
      // Remove _all_ overlapping regions, and parts of regions,
      // in preparation for the addition of this new region.
      remove_uncommitted_region(addr, size);

      // The remove could have split a region into two and created a
      // new prev region. Need to reset the prev and next pointers.
      prev = find_preceding_node_from((prev != NULL ? prev : _committed_regions.head()), addr);
      next = (prev != NULL ? prev->next() : _committed_regions.head());
    }
  }

  // At this point the previous overlapping regions have been
  // cleared, and the full region is guaranteed to be inserted.
  VirtualMemorySummary::record_committed_memory(size, flag());

  // Try to merge with prev and possibly next.
  if (try_merge_with(prev, addr, size, stack)) {
    if (try_merge_with(prev, next)) {
      // prev was expanded to contain the new region
      // and next, need to remove next from the list
      _committed_regions.remove_after(prev);
    }

    return true;
  }

  // Didn't merge with prev, try with next.
  if (try_merge_with(next, addr, size, stack)) {
    return true;
  }

  // Couldn't merge with any regions - create a new region.
  return add_committed_region(CommittedMemoryRegion(addr, size, stack));
}

bool ReservedMemoryRegion::remove_uncommitted_region(LinkedListNode<CommittedMemoryRegion>* node,
  address addr, size_t size) {
  assert(addr != NULL, "Invalid address");
  assert(size > 0, "Invalid size");

  CommittedMemoryRegion* rgn = node->data();
  assert(rgn->contain_region(addr, size), "Has to be contained");
  assert(!rgn->same_region(addr, size), "Can not be the same region");

  if (rgn->base() == addr ||
      rgn->end() == addr + size) {
    rgn->exclude_region(addr, size);
    return true;
  } else {
    // split this region
    address top =rgn->end();
    // use this region for lower part
    size_t exclude_size = rgn->end() - addr;
    rgn->exclude_region(addr, exclude_size);

    // higher part
    address high_base = addr + size;
    size_t  high_size = top - high_base;

    CommittedMemoryRegion high_rgn(high_base, high_size, *rgn->call_stack());
    LinkedListNode<CommittedMemoryRegion>* high_node = _committed_regions.add(high_rgn);
    assert(high_node == NULL || node->next() == high_node, "Should be right after");
    return (high_node != NULL);
  }

  return false;
}

bool ReservedMemoryRegion::remove_uncommitted_region(address addr, size_t sz) {
  assert(addr != NULL, "Invalid address");
  assert(sz > 0, "Invalid size");

  CommittedMemoryRegion del_rgn(addr, sz, *call_stack());
  address end = addr + sz;

  LinkedListNode<CommittedMemoryRegion>* head = _committed_regions.head();
  LinkedListNode<CommittedMemoryRegion>* prev = NULL;
  CommittedMemoryRegion* crgn;

  while (head != NULL) {
    crgn = head->data();

    if (crgn->same_region(addr, sz)) {
      VirtualMemorySummary::record_uncommitted_memory(crgn->size(), flag());
      _committed_regions.remove_after(prev);
      return true;
    }

    // del_rgn contains crgn
    if (del_rgn.contain_region(crgn->base(), crgn->size())) {
      VirtualMemorySummary::record_uncommitted_memory(crgn->size(), flag());
      head = head->next();
      _committed_regions.remove_after(prev);
      continue;  // don't update head or prev
    }

    // Found addr in the current crgn. There are 2 subcases:
    if (crgn->contain_address(addr)) {

      // (1) Found addr+size in current crgn as well. (del_rgn is contained in crgn)
      if (crgn->contain_address(end - 1)) {
        VirtualMemorySummary::record_uncommitted_memory(sz, flag());
        return remove_uncommitted_region(head, addr, sz); // done!
      } else {
        // (2) Did not find del_rgn's end in crgn.
        size_t size = crgn->end() - del_rgn.base();
        crgn->exclude_region(addr, size);
        VirtualMemorySummary::record_uncommitted_memory(size, flag());
      }

    } else if (crgn->contain_address(end - 1)) {
      // Found del_rgn's end, but not its base addr.
      size_t size = del_rgn.end() - crgn->base();
      crgn->exclude_region(crgn->base(), size);
      VirtualMemorySummary::record_uncommitted_memory(size, flag());
      return true;  // should be done if the list is sorted properly!
    }

    prev = head;
    head = head->next();
  }

  return true;
}

void ReservedMemoryRegion::move_committed_regions(address addr, ReservedMemoryRegion& rgn) {
  assert(addr != NULL, "Invalid address");

  // split committed regions
  LinkedListNode<CommittedMemoryRegion>* head =
    _committed_regions.head();
  LinkedListNode<CommittedMemoryRegion>* prev = NULL;

  while (head != NULL) {
    if (head->data()->base() >= addr) {
      break;
    }
    prev = head;
    head = head->next();
  }

  if (head != NULL) {
    if (prev != NULL) {
      prev->set_next(head->next());
    } else {
      _committed_regions.set_head(NULL);
    }
  }

  rgn._committed_regions.set_head(head);
}

size_t ReservedMemoryRegion::committed_size() const {
  size_t committed = 0;
  LinkedListNode<CommittedMemoryRegion>* head =
    _committed_regions.head();
  while (head != NULL) {
    committed += head->data()->size();
    head = head->next();
  }
  return committed;
}

void ReservedMemoryRegion::set_flag(MEMFLAGS f) {
  assert((flag() == mtNone || flag() == f), "Overwrite memory type");
  if (flag() != f) {
    VirtualMemorySummary::move_reserved_memory(flag(), f, size());
    VirtualMemorySummary::move_committed_memory(flag(), f, committed_size());
    _flag = f;
  }
}

address ReservedMemoryRegion::thread_stack_uncommitted_bottom() const {
  assert(flag() == mtThreadStack, "Only for thread stack");
  LinkedListNode<CommittedMemoryRegion>* head = _committed_regions.head();
  address bottom = base();
  address top = base() + size();
  while (head != NULL) {
    address committed_top = head->data()->base() + head->data()->size();
    if (committed_top < top) {
      // committed stack guard pages, skip them
      bottom = head->data()->base() + head->data()->size();
      head = head->next();
    } else {
      assert(top == committed_top, "Sanity");
      break;
    }
  }

  return bottom;
}

bool VirtualMemoryTracker::initialize(NMT_TrackingLevel level) {
  if (level >= NMT_summary) {
    VirtualMemorySummary::initialize();
  }
  return true;
}

bool VirtualMemoryTracker::late_initialize(NMT_TrackingLevel level) {
  if (level >= NMT_summary) {
    _reserved_regions = new (std::nothrow, ResourceObj::C_HEAP, mtNMT)
      SortedLinkedList<ReservedMemoryRegion, compare_reserved_region_base>();
    return (_reserved_regions != NULL);
  }
  return true;
}

bool VirtualMemoryTracker::add_reserved_region(address base_addr, size_t size,
    const NativeCallStack& stack, MEMFLAGS flag) {
  assert(base_addr != NULL, "Invalid address");
  assert(size > 0, "Invalid size");
  assert(_reserved_regions != NULL, "Sanity check");
  ReservedMemoryRegion  rgn(base_addr, size, stack, flag);
  ReservedMemoryRegion* reserved_rgn = _reserved_regions->find(rgn);

  if (reserved_rgn == NULL) {
    VirtualMemorySummary::record_reserved_memory(size, flag);
    return _reserved_regions->add(rgn) != NULL;
  } else {
    if (reserved_rgn->same_region(base_addr, size)) {
      reserved_rgn->set_call_stack(stack);
      reserved_rgn->set_flag(flag);
      return true;
    } else if (reserved_rgn->adjacent_to(base_addr, size)) {
      VirtualMemorySummary::record_reserved_memory(size, flag);
      reserved_rgn->expand_region(base_addr, size);
      reserved_rgn->set_call_stack(stack);
      return true;
    } else {
      // Overlapped reservation.
      // It can happen when the regions are thread stacks, as JNI
      // thread does not detach from VM before exits, and leads to
      // leak JavaThread object
      if (reserved_rgn->flag() == mtThreadStack) {
        guarantee(!CheckJNICalls, "Attached JNI thread exited without being detached");
        // Overwrite with new region

        // Release old region
        VirtualMemorySummary::record_uncommitted_memory(reserved_rgn->committed_size(), reserved_rgn->flag());
        VirtualMemorySummary::record_released_memory(reserved_rgn->size(), reserved_rgn->flag());

        // Add new region
        VirtualMemorySummary::record_reserved_memory(rgn.size(), flag);

        *reserved_rgn = rgn;
        return true;
      }

      // CDS mapping region.
      // CDS reserves the whole region for mapping CDS archive, then maps each section into the region.
      // NMT reports CDS as a whole.
      if (reserved_rgn->flag() == mtClassShared) {
        assert(reserved_rgn->contain_region(base_addr, size), "Reserved CDS region should contain this mapping region");
        return true;
      }

      // Mapped CDS string region.
      // The string region(s) is part of the java heap.
      if (reserved_rgn->flag() == mtJavaHeap) {
        assert(reserved_rgn->contain_region(base_addr, size), "Reserved heap region should contain this mapping region");
        return true;
      }

      ShouldNotReachHere();
      return false;
    }
  }
}

void VirtualMemoryTracker::set_reserved_region_type(address addr, MEMFLAGS flag) {
  assert(addr != NULL, "Invalid address");
  assert(_reserved_regions != NULL, "Sanity check");

  ReservedMemoryRegion   rgn(addr, 1);
  ReservedMemoryRegion*  reserved_rgn = _reserved_regions->find(rgn);
  if (reserved_rgn != NULL) {
    assert(reserved_rgn->contain_address(addr), "Containment");
    if (reserved_rgn->flag() != flag) {
      assert(reserved_rgn->flag() == mtNone, "Overwrite memory type");
      reserved_rgn->set_flag(flag);
    }
  }
}

bool VirtualMemoryTracker::add_committed_region(address addr, size_t size,
  const NativeCallStack& stack) {
  assert(addr != NULL, "Invalid address");
  assert(size > 0, "Invalid size");
  assert(_reserved_regions != NULL, "Sanity check");

  ReservedMemoryRegion  rgn(addr, size);
  ReservedMemoryRegion* reserved_rgn = _reserved_regions->find(rgn);

  assert(reserved_rgn != NULL, "No reserved region");
  assert(reserved_rgn->contain_region(addr, size), "Not completely contained");
  bool result = reserved_rgn->add_committed_region(addr, size, stack);
  return result;
}

bool VirtualMemoryTracker::remove_uncommitted_region(address addr, size_t size) {
  assert(addr != NULL, "Invalid address");
  assert(size > 0, "Invalid size");
  assert(_reserved_regions != NULL, "Sanity check");

  ReservedMemoryRegion  rgn(addr, size);
  ReservedMemoryRegion* reserved_rgn = _reserved_regions->find(rgn);
  assert(reserved_rgn != NULL, "No reserved region");
  assert(reserved_rgn->contain_region(addr, size), "Not completely contained");
  bool result = reserved_rgn->remove_uncommitted_region(addr, size);
  return result;
}

bool VirtualMemoryTracker::remove_released_region(address addr, size_t size) {
  assert(addr != NULL, "Invalid address");
  assert(size > 0, "Invalid size");
  assert(_reserved_regions != NULL, "Sanity check");

  ReservedMemoryRegion  rgn(addr, size);
  ReservedMemoryRegion* reserved_rgn = _reserved_regions->find(rgn);

  assert(reserved_rgn != NULL, "No reserved region");

  // uncommit regions within the released region
  if (!reserved_rgn->remove_uncommitted_region(addr, size)) {
    return false;
  }

  if (reserved_rgn->flag() == mtClassShared &&
      reserved_rgn->contain_region(addr, size) &&
      !reserved_rgn->same_region(addr, size)) {
    // This is an unmapped CDS region, which is part of the reserved shared
    // memory region.
    // See special handling in VirtualMemoryTracker::add_reserved_region also.
    return true;
  }

  VirtualMemorySummary::record_released_memory(size, reserved_rgn->flag());

  if (reserved_rgn->same_region(addr, size)) {
    return _reserved_regions->remove(rgn);
  } else {
    assert(reserved_rgn->contain_region(addr, size), "Not completely contained");
    if (reserved_rgn->base() == addr ||
        reserved_rgn->end() == addr + size) {
        reserved_rgn->exclude_region(addr, size);
      return true;
    } else {
      address top = reserved_rgn->end();
      address high_base = addr + size;
      ReservedMemoryRegion high_rgn(high_base, top - high_base,
        *reserved_rgn->call_stack(), reserved_rgn->flag());

      // use original region for lower region
      reserved_rgn->exclude_region(addr, top - addr);
      LinkedListNode<ReservedMemoryRegion>* new_rgn = _reserved_regions->add(high_rgn);
      if (new_rgn == NULL) {
        return false;
      } else {
        reserved_rgn->move_committed_regions(addr, *new_rgn->data());
        return true;
      }
    }
  }
}

// Iterate the range, find committed region within its bound.
class RegionIterator : public StackObj {
private:
  const address _start;
  const size_t  _size;

  address _current_start;
  size_t  _current_size;
public:
  RegionIterator(address start, size_t size) :
    _start(start), _size(size), _current_start(start), _current_size(size) {
  }

  // return true if committed region is found
  bool next_committed(address& start, size_t& size);
private:
  address end() const { return _start + _size; }
};

bool RegionIterator::next_committed(address& committed_start, size_t& committed_size) {
  if (end() <= _current_start) return false;

  const size_t page_sz = os::vm_page_size();
  assert(_current_start + _current_size == end(), "Must be");
  if (os::committed_in_range(_current_start, _current_size, committed_start, committed_size)) {
    assert(committed_start != NULL, "Must be");
    assert(committed_size > 0 && is_aligned(committed_size, os::vm_page_size()), "Must be");

    size_t remaining_size = (_current_start + _current_size) - (committed_start + committed_size);
    _current_start = committed_start + committed_size;
    _current_size = remaining_size;
    return true;
  } else {
    return false;
  }
}

// Walk all known thread stacks, snapshot their committed ranges.
class SnapshotThreadStackWalker : public VirtualMemoryWalker {
public:
  SnapshotThreadStackWalker() {}

  bool do_allocation_site(const ReservedMemoryRegion* rgn) {
    if (rgn->flag() == mtThreadStack) {
      address stack_bottom = rgn->thread_stack_uncommitted_bottom();
      address committed_start;
      size_t  committed_size;
      size_t stack_size = rgn->base() + rgn->size() - stack_bottom;

      ReservedMemoryRegion* region = const_cast<ReservedMemoryRegion*>(rgn);
      NativeCallStack ncs; // empty stack

      RegionIterator itr(stack_bottom, stack_size);
      DEBUG_ONLY(bool found_stack = false;)
      while (itr.next_committed(committed_start, committed_size)) {
        assert(committed_start != NULL, "Should not be null");
        assert(committed_size > 0, "Should not be 0");
        region->add_committed_region(committed_start, committed_size, ncs);
        DEBUG_ONLY(found_stack = true;)
      }
#ifdef ASSERT
      if (!found_stack) {
        log_debug(thread)("Thread exited without proper cleanup, may leak thread object");
      }
#endif
    }
    return true;
  }
};

void VirtualMemoryTracker::snapshot_thread_stacks() {
  SnapshotThreadStackWalker walker;
  walk_virtual_memory(&walker);
}

bool VirtualMemoryTracker::walk_virtual_memory(VirtualMemoryWalker* walker) {
  assert(_reserved_regions != NULL, "Sanity check");
  ThreadCritical tc;
  // Check that the _reserved_regions haven't been deleted.
  if (_reserved_regions != NULL) {
    LinkedListNode<ReservedMemoryRegion>* head = _reserved_regions->head();
    while (head != NULL) {
      const ReservedMemoryRegion* rgn = head->peek();
      if (!walker->do_allocation_site(rgn)) {
        return false;
      }
      head = head->next();
    }
   }
  return true;
}

// Transition virtual memory tracking level.
bool VirtualMemoryTracker::transition(NMT_TrackingLevel from, NMT_TrackingLevel to) {
  assert (from != NMT_minimal, "cannot convert from the lowest tracking level to anything");
  if (to == NMT_minimal) {
    assert(from == NMT_summary || from == NMT_detail, "Just check");
    // Clean up virtual memory tracking data structures.
    ThreadCritical tc;
    // Check for potential race with other thread calling transition
    if (_reserved_regions != NULL) {
      delete _reserved_regions;
      _reserved_regions = NULL;
    }
  }

  return true;
}

// Metaspace Support
MetaspaceSnapshot::MetaspaceSnapshot() {
  for (int index = (int)Metaspace::ClassType; index < (int)Metaspace::MetadataTypeCount; index ++) {
    Metaspace::MetadataType type = (Metaspace::MetadataType)index;
    assert_valid_metadata_type(type);
    _reserved_in_bytes[type]  = 0;
    _committed_in_bytes[type] = 0;
    _used_in_bytes[type]      = 0;
    _free_in_bytes[type]      = 0;
  }
}

void MetaspaceSnapshot::snapshot(Metaspace::MetadataType type, MetaspaceSnapshot& mss) {
  assert_valid_metadata_type(type);

  mss._reserved_in_bytes[type]   = MetaspaceUtils::reserved_bytes(type);
  mss._committed_in_bytes[type]  = MetaspaceUtils::committed_bytes(type);
  mss._used_in_bytes[type]       = MetaspaceUtils::used_bytes(type);

  size_t free_in_bytes = (MetaspaceUtils::capacity_bytes(type) - MetaspaceUtils::used_bytes(type))
                       + MetaspaceUtils::free_chunks_total_bytes(type)
                       + MetaspaceUtils::free_in_vs_bytes(type);
  mss._free_in_bytes[type] = free_in_bytes;
}

void MetaspaceSnapshot::snapshot(MetaspaceSnapshot& mss) {
  snapshot(Metaspace::ClassType, mss);
  if (Metaspace::using_class_space()) {
    snapshot(Metaspace::NonClassType, mss);
  }
}
