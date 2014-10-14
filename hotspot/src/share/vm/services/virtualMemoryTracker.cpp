/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/threadCritical.hpp"
#include "services/virtualMemoryTracker.hpp"

size_t VirtualMemorySummary::_snapshot[CALC_OBJ_SIZE_IN_TYPE(VirtualMemorySnapshot, size_t)];

void VirtualMemorySummary::initialize() {
  assert(sizeof(_snapshot) >= sizeof(VirtualMemorySnapshot), "Sanity Check");
  // Use placement operator new to initialize static data area.
  ::new ((void*)_snapshot) VirtualMemorySnapshot();
}

SortedLinkedList<ReservedMemoryRegion, compare_reserved_region_base>* VirtualMemoryTracker::_reserved_regions;

int compare_committed_region(const CommittedMemoryRegion& r1, const CommittedMemoryRegion& r2) {
  return r1.compare(r2);
}

int compare_reserved_region_base(const ReservedMemoryRegion& r1, const ReservedMemoryRegion& r2) {
  return r1.compare(r2);
}

bool ReservedMemoryRegion::add_committed_region(address addr, size_t size, const NativeCallStack& stack) {
  assert(addr != NULL, "Invalid address");
  assert(size > 0, "Invalid size");
  assert(contain_region(addr, size), "Not contain this region");

  if (all_committed()) return true;

  CommittedMemoryRegion committed_rgn(addr, size, stack);
  LinkedListNode<CommittedMemoryRegion>* node = _committed_regions.find_node(committed_rgn);
  if (node != NULL) {
    CommittedMemoryRegion* rgn = node->data();
    if (rgn->same_region(addr, size)) {
      return true;
    }

    if (rgn->adjacent_to(addr, size)) {
      // check if the next region covers this committed region,
      // the regions may not be merged due to different call stacks
      LinkedListNode<CommittedMemoryRegion>* next =
        node->next();
      if (next != NULL && next->data()->contain_region(addr, size)) {
        if (next->data()->same_region(addr, size)) {
          next->data()->set_call_stack(stack);
        }
        return true;
      }
      if (rgn->call_stack()->equals(stack)) {
        VirtualMemorySummary::record_uncommitted_memory(rgn->size(), flag());
        // the two adjacent regions have the same call stack, merge them
        rgn->expand_region(addr, size);
        VirtualMemorySummary::record_committed_memory(rgn->size(), flag());
        return true;
      }
      VirtualMemorySummary::record_committed_memory(size, flag());
      if (rgn->base() > addr) {
        return _committed_regions.insert_before(committed_rgn, node) != NULL;
      } else {
        return _committed_regions.insert_after(committed_rgn, node) != NULL;
      }
    }
    assert(rgn->contain_region(addr, size), "Must cover this region");
    return true;
  } else {
    // New committed region
    VirtualMemorySummary::record_committed_memory(size, flag());
    return add_committed_region(committed_rgn);
  }
}

void ReservedMemoryRegion::set_all_committed(bool b) {
  if (all_committed() != b) {
    _all_committed = b;
    if (b) {
      VirtualMemorySummary::record_committed_memory(size(), flag());
    }
  }
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
  // uncommit stack guard pages
  if (flag() == mtThreadStack && !same_region(addr, sz)) {
    return true;
  }

  assert(addr != NULL, "Invalid address");
  assert(sz > 0, "Invalid size");

  if (all_committed()) {
    assert(_committed_regions.is_empty(), "Sanity check");
    assert(contain_region(addr, sz), "Reserved region does not contain this region");
    set_all_committed(false);
    VirtualMemorySummary::record_uncommitted_memory(sz, flag());
    if (same_region(addr, sz)) {
      return true;
    } else {
      CommittedMemoryRegion rgn(base(), size(), *call_stack());
      if (rgn.base() == addr || rgn.end() == (addr + sz)) {
        rgn.exclude_region(addr, sz);
        return add_committed_region(rgn);
      } else {
        // split this region
        // top of the whole region
        address top =rgn.end();
        // use this region for lower part
        size_t exclude_size = rgn.end() - addr;
        rgn.exclude_region(addr, exclude_size);
        if (add_committed_region(rgn)) {
          // higher part
          address high_base = addr + sz;
          size_t  high_size = top - high_base;
          CommittedMemoryRegion high_rgn(high_base, high_size, NativeCallStack::EMPTY_STACK);
          return add_committed_region(high_rgn);
        } else {
          return false;
        }
      }
    }
  } else {
    // we have to walk whole list to remove the committed regions in
    // specified range
    LinkedListNode<CommittedMemoryRegion>* head =
      _committed_regions.head();
    LinkedListNode<CommittedMemoryRegion>* prev = NULL;
    VirtualMemoryRegion uncommitted_rgn(addr, sz);

    while (head != NULL && !uncommitted_rgn.is_empty()) {
      CommittedMemoryRegion* crgn = head->data();
      // this committed region overlaps to region to uncommit
      if (crgn->overlap_region(uncommitted_rgn.base(), uncommitted_rgn.size())) {
        if (crgn->same_region(uncommitted_rgn.base(), uncommitted_rgn.size())) {
          // find matched region, remove the node will do
          VirtualMemorySummary::record_uncommitted_memory(uncommitted_rgn.size(), flag());
          _committed_regions.remove_after(prev);
          return true;
        } else if (crgn->contain_region(uncommitted_rgn.base(), uncommitted_rgn.size())) {
          // this committed region contains whole uncommitted region
          VirtualMemorySummary::record_uncommitted_memory(uncommitted_rgn.size(), flag());
          return remove_uncommitted_region(head, uncommitted_rgn.base(), uncommitted_rgn.size());
        } else if (uncommitted_rgn.contain_region(crgn->base(), crgn->size())) {
          // this committed region has been uncommitted
          size_t exclude_size = crgn->end() - uncommitted_rgn.base();
          uncommitted_rgn.exclude_region(uncommitted_rgn.base(), exclude_size);
          VirtualMemorySummary::record_uncommitted_memory(crgn->size(), flag());
          LinkedListNode<CommittedMemoryRegion>* tmp = head;
          head = head->next();
          _committed_regions.remove_after(prev);
          continue;
        } else if (crgn->contain_address(uncommitted_rgn.base())) {
          size_t toUncommitted = crgn->end() - uncommitted_rgn.base();
          crgn->exclude_region(uncommitted_rgn.base(), toUncommitted);
          uncommitted_rgn.exclude_region(uncommitted_rgn.base(), toUncommitted);
          VirtualMemorySummary::record_uncommitted_memory(toUncommitted, flag());
        } else if (uncommitted_rgn.contain_address(crgn->base())) {
          size_t toUncommitted = uncommitted_rgn.end() - crgn->base();
          crgn->exclude_region(crgn->base(), toUncommitted);
          uncommitted_rgn.exclude_region(uncommitted_rgn.end() - toUncommitted,
            toUncommitted);
          VirtualMemorySummary::record_uncommitted_memory(toUncommitted, flag());
        }
      }
      prev = head;
      head = head->next();
    }
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
  if (all_committed()) {
    return size();
  } else {
    size_t committed = 0;
    LinkedListNode<CommittedMemoryRegion>* head =
      _committed_regions.head();
    while (head != NULL) {
      committed += head->data()->size();
      head = head->next();
    }
    return committed;
  }
}

void ReservedMemoryRegion::set_flag(MEMFLAGS f) {
  assert((flag() == mtNone || flag() == f), "Overwrite memory type");
  if (flag() != f) {
    VirtualMemorySummary::move_reserved_memory(flag(), f, size());
    VirtualMemorySummary::move_committed_memory(flag(), f, committed_size());
    _flag = f;
  }
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
   const NativeCallStack& stack, MEMFLAGS flag, bool all_committed) {
  assert(base_addr != NULL, "Invalid address");
  assert(size > 0, "Invalid size");
  assert(_reserved_regions != NULL, "Sanity check");
  ReservedMemoryRegion  rgn(base_addr, size, stack, flag);
  ReservedMemoryRegion* reserved_rgn = _reserved_regions->find(rgn);
  LinkedListNode<ReservedMemoryRegion>* node;
  if (reserved_rgn == NULL) {
    VirtualMemorySummary::record_reserved_memory(size, flag);
    node = _reserved_regions->add(rgn);
    if (node != NULL) {
      node->data()->set_all_committed(all_committed);
      return true;
    } else {
      return false;
    }
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
  return reserved_rgn->add_committed_region(addr, size, stack);
}

bool VirtualMemoryTracker::remove_uncommitted_region(address addr, size_t size) {
  assert(addr != NULL, "Invalid address");
  assert(size > 0, "Invalid size");
  assert(_reserved_regions != NULL, "Sanity check");

  ReservedMemoryRegion  rgn(addr, size);
  ReservedMemoryRegion* reserved_rgn = _reserved_regions->find(rgn);
  assert(reserved_rgn != NULL, "No reserved region");
  assert(reserved_rgn->contain_region(addr, size), "Not completely contained");
  return reserved_rgn->remove_uncommitted_region(addr, size);
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


