/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/metaspaceStats.hpp"
#include "memory/metaspaceUtils.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/nativeCallStackPrinter.hpp"
#include "nmt/threadStackTracker.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "runtime/os.hpp"
#include "utilities/ostream.hpp"

VirtualMemorySnapshot VirtualMemorySummary::_snapshot;

void VirtualMemory::update_peak(size_t size) {
  size_t peak_sz = peak_size();
  while (peak_sz < size) {
    size_t old_sz = Atomic::cmpxchg(&_peak_size, peak_sz, size, memory_order_relaxed);
    if (old_sz == peak_sz) {
      break;
    } else {
      peak_sz = old_sz;
    }
  }
}

void VirtualMemorySummary::snapshot(VirtualMemorySnapshot* s) {
  // Snapshot current thread stacks
  VirtualMemoryTracker::snapshot_thread_stacks();
  as_snapshot()->copy_to(s);
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
  LinkedListNode<CommittedMemoryRegion>* preceding = nullptr;

  for (LinkedListNode<CommittedMemoryRegion>* node = from; node != nullptr; node = node->next()) {
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
  if (node != nullptr) {
    CommittedMemoryRegion* rgn = node->data();

    if (is_mergeable_with(rgn, addr, size, stack)) {
      rgn->expand_region(addr, size);
      return true;
    }
  }

  return false;
}

static bool try_merge_with(LinkedListNode<CommittedMemoryRegion>* node, LinkedListNode<CommittedMemoryRegion>* other) {
  if (other == nullptr) {
    return false;
  }

  CommittedMemoryRegion* rgn = other->data();
  return try_merge_with(node, rgn->base(), rgn->size(), *rgn->call_stack());
}

bool ReservedMemoryRegion::add_committed_region(address addr, size_t size, const NativeCallStack& stack) {
  assert(addr != nullptr, "Invalid address");
  assert(size > 0, "Invalid size");
  assert(contain_region(addr, size), "Not contain this region");

  // Find the region that fully precedes the [addr, addr + size) region.
  LinkedListNode<CommittedMemoryRegion>* prev = find_preceding_node_from(_committed_regions.head(), addr);
  LinkedListNode<CommittedMemoryRegion>* next = (prev != nullptr ? prev->next() : _committed_regions.head());

  if (next != nullptr) {
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
      prev = find_preceding_node_from((prev != nullptr ? prev : _committed_regions.head()), addr);
      next = (prev != nullptr ? prev->next() : _committed_regions.head());
    }
  }

  // At this point the previous overlapping regions have been
  // cleared, and the full region is guaranteed to be inserted.
  VirtualMemorySummary::record_committed_memory(size, mem_tag());

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
  assert(addr != nullptr, "Invalid address");
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
    assert(high_node == nullptr || node->next() == high_node, "Should be right after");
    return (high_node != nullptr);
  }

  return false;
}

bool ReservedMemoryRegion::remove_uncommitted_region(address addr, size_t sz) {
  assert(addr != nullptr, "Invalid address");
  assert(sz > 0, "Invalid size");

  CommittedMemoryRegion del_rgn(addr, sz, *call_stack());
  address end = addr + sz;

  LinkedListNode<CommittedMemoryRegion>* head = _committed_regions.head();
  LinkedListNode<CommittedMemoryRegion>* prev = nullptr;
  CommittedMemoryRegion* crgn;

  while (head != nullptr) {
    crgn = head->data();

    if (crgn->same_region(addr, sz)) {
      VirtualMemorySummary::record_uncommitted_memory(crgn->size(), mem_tag());
      _committed_regions.remove_after(prev);
      return true;
    }

    // del_rgn contains crgn
    if (del_rgn.contain_region(crgn->base(), crgn->size())) {
      VirtualMemorySummary::record_uncommitted_memory(crgn->size(), mem_tag());
      head = head->next();
      _committed_regions.remove_after(prev);
      continue;  // don't update head or prev
    }

    // Found addr in the current crgn. There are 2 subcases:
    if (crgn->contain_address(addr)) {

      // (1) Found addr+size in current crgn as well. (del_rgn is contained in crgn)
      if (crgn->contain_address(end - 1)) {
        VirtualMemorySummary::record_uncommitted_memory(sz, mem_tag());
        return remove_uncommitted_region(head, addr, sz); // done!
      } else {
        // (2) Did not find del_rgn's end in crgn.
        size_t size = crgn->end() - del_rgn.base();
        crgn->exclude_region(addr, size);
        VirtualMemorySummary::record_uncommitted_memory(size, mem_tag());
      }

    } else if (crgn->contain_address(end - 1)) {
      // Found del_rgn's end, but not its base addr.
      size_t size = del_rgn.end() - crgn->base();
      crgn->exclude_region(crgn->base(), size);
      VirtualMemorySummary::record_uncommitted_memory(size, mem_tag());
      return true;  // should be done if the list is sorted properly!
    }

    prev = head;
    head = head->next();
  }

  return true;
}

void ReservedMemoryRegion::move_committed_regions(address addr, ReservedMemoryRegion& rgn) {
  assert(addr != nullptr, "Invalid address");

  // split committed regions
  LinkedListNode<CommittedMemoryRegion>* head =
    _committed_regions.head();
  LinkedListNode<CommittedMemoryRegion>* prev = nullptr;

  while (head != nullptr) {
    if (head->data()->base() >= addr) {
      break;
    }
    prev = head;
    head = head->next();
  }

  if (head != nullptr) {
    if (prev != nullptr) {
      prev->set_next(head->next());
    } else {
      _committed_regions.set_head(nullptr);
    }
  }

  rgn._committed_regions.set_head(head);
}

size_t ReservedMemoryRegion::committed_size() const {
  size_t committed = 0;
  LinkedListNode<CommittedMemoryRegion>* head =
    _committed_regions.head();
  while (head != nullptr) {
    committed += head->data()->size();
    head = head->next();
  }
  return committed;
}

void ReservedMemoryRegion::set_mem_tag(MemTag new_mem_tag) {
  assert((mem_tag() == mtNone || mem_tag() == new_mem_tag),
         "Overwrite memory tag for region [" INTPTR_FORMAT "-" INTPTR_FORMAT "), %u->%u.",
         p2i(base()), p2i(end()), (unsigned)mem_tag(), (unsigned)new_mem_tag);
  if (mem_tag() != new_mem_tag) {
    VirtualMemorySummary::move_reserved_memory(mem_tag(), new_mem_tag, size());
    VirtualMemorySummary::move_committed_memory(mem_tag(), new_mem_tag, committed_size());
    _mem_tag = new_mem_tag;
  }
}

address ReservedMemoryRegion::thread_stack_uncommitted_bottom() const {
  assert(mem_tag() == mtThreadStack, "Only for thread stack");
  LinkedListNode<CommittedMemoryRegion>* head = _committed_regions.head();
  address bottom = base();
  address top = base() + size();
  while (head != nullptr) {
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
  assert(_reserved_regions == nullptr, "only call once");
  if (level >= NMT_summary) {
    _reserved_regions = new (std::nothrow, mtNMT)
      SortedLinkedList<ReservedMemoryRegion, compare_reserved_region_base>();
    return (_reserved_regions != nullptr);
  }
  return true;
}

bool VirtualMemoryTracker::add_reserved_region(address base_addr, size_t size,
    const NativeCallStack& stack, MemTag mem_tag) {
  assert(base_addr != nullptr, "Invalid address");
  assert(size > 0, "Invalid size");
  assert(_reserved_regions != nullptr, "Sanity check");
  ReservedMemoryRegion  rgn(base_addr, size, stack, mem_tag);
  ReservedMemoryRegion* reserved_rgn = _reserved_regions->find(rgn);

  log_debug(nmt)("Add reserved region \'%s\' (" INTPTR_FORMAT ", " SIZE_FORMAT ")",
                rgn.mem_tag_name(), p2i(rgn.base()), rgn.size());
  if (reserved_rgn == nullptr) {
    VirtualMemorySummary::record_reserved_memory(size, mem_tag);
    return _reserved_regions->add(rgn) != nullptr;
  } else {
    // Deal with recursive reservation
    // os::reserve_memory() -> pd_reserve_memory() -> os::reserve_memory()
    // See JDK-8198226.
    if (reserved_rgn->same_region(base_addr, size) &&
        (reserved_rgn->mem_tag() == mem_tag || reserved_rgn->mem_tag() == mtNone)) {
      reserved_rgn->set_call_stack(stack);
      reserved_rgn->set_mem_tag(mem_tag);
      return true;
    } else {
      assert(reserved_rgn->overlap_region(base_addr, size), "Must be");

      // Overlapped reservation.
      // It can happen when the regions are thread stacks, as JNI
      // thread does not detach from VM before exits, and leads to
      // leak JavaThread object
      if (reserved_rgn->mem_tag() == mtThreadStack) {
        guarantee(!CheckJNICalls, "Attached JNI thread exited without being detached");
        // Overwrite with new region

        // Release old region
        VirtualMemorySummary::record_uncommitted_memory(reserved_rgn->committed_size(), reserved_rgn->mem_tag());
        VirtualMemorySummary::record_released_memory(reserved_rgn->size(), reserved_rgn->mem_tag());

        // Add new region
        VirtualMemorySummary::record_reserved_memory(rgn.size(), mem_tag);

        *reserved_rgn = rgn;
        return true;
      }

      // CDS mapping region.
      // CDS reserves the whole region for mapping CDS archive, then maps each section into the region.
      // NMT reports CDS as a whole.
      if (reserved_rgn->mem_tag() == mtClassShared) {
        log_debug(nmt)("CDS reserved region \'%s\' as a whole (" INTPTR_FORMAT ", " SIZE_FORMAT ")",
                      reserved_rgn->mem_tag_name(), p2i(reserved_rgn->base()), reserved_rgn->size());
        assert(reserved_rgn->contain_region(base_addr, size), "Reserved CDS region should contain this mapping region");
        return true;
      }

      // Mapped CDS string region.
      // The string region(s) is part of the java heap.
      if (reserved_rgn->mem_tag() == mtJavaHeap) {
        log_debug(nmt)("CDS reserved region \'%s\' as a whole (" INTPTR_FORMAT ", " SIZE_FORMAT ")",
                      reserved_rgn->mem_tag_name(), p2i(reserved_rgn->base()), reserved_rgn->size());
        assert(reserved_rgn->contain_region(base_addr, size), "Reserved heap region should contain this mapping region");
        return true;
      }

      // Print some more details. Don't use UL here to avoid circularities.
      tty->print_cr("Error: existing region: [" INTPTR_FORMAT "-" INTPTR_FORMAT "), memory tag %u.\n"
                    "       new region: [" INTPTR_FORMAT "-" INTPTR_FORMAT "), memory tag %u.",
                    p2i(reserved_rgn->base()), p2i(reserved_rgn->end()), (unsigned)reserved_rgn->mem_tag(),
                    p2i(base_addr), p2i(base_addr + size), (unsigned)mem_tag);
      if (MemTracker::tracking_level() == NMT_detail) {
        tty->print_cr("Existing region allocated from:");
        reserved_rgn->call_stack()->print_on(tty);
        tty->print_cr("New region allocated from:");
        stack.print_on(tty);
      }
      ShouldNotReachHere();
      return false;
    }
  }
}

void VirtualMemoryTracker::set_reserved_region_type(address addr, MemTag mem_tag) {
  assert(addr != nullptr, "Invalid address");
  assert(_reserved_regions != nullptr, "Sanity check");

  ReservedMemoryRegion   rgn(addr, 1);
  ReservedMemoryRegion*  reserved_rgn = _reserved_regions->find(rgn);
  if (reserved_rgn != nullptr) {
    assert(reserved_rgn->contain_address(addr), "Containment");
    if (reserved_rgn->mem_tag() != mem_tag) {
      assert(reserved_rgn->mem_tag() == mtNone, "Overwrite memory tag (should be mtNone, is: \"%s\")",
             NMTUtil::tag_to_name(reserved_rgn->mem_tag()));
      reserved_rgn->set_mem_tag(mem_tag);
    }
  }
}

bool VirtualMemoryTracker::add_committed_region(address addr, size_t size,
  const NativeCallStack& stack) {
  assert(addr != nullptr, "Invalid address");
  assert(size > 0, "Invalid size");
  assert(_reserved_regions != nullptr, "Sanity check");

  ReservedMemoryRegion  rgn(addr, size);
  ReservedMemoryRegion* reserved_rgn = _reserved_regions->find(rgn);

  if (reserved_rgn == nullptr) {
    log_debug(nmt)("Add committed region \'%s\', No reserved region found for  (" INTPTR_FORMAT ", " SIZE_FORMAT ")",
                  rgn.mem_tag_name(),  p2i(rgn.base()), rgn.size());
  }
  assert(reserved_rgn != nullptr, "Add committed region, No reserved region found");
  assert(reserved_rgn->contain_region(addr, size), "Not completely contained");
  bool result = reserved_rgn->add_committed_region(addr, size, stack);
  log_debug(nmt)("Add committed region \'%s\'(" INTPTR_FORMAT ", " SIZE_FORMAT ") %s",
                reserved_rgn->mem_tag_name(),  p2i(rgn.base()), rgn.size(), (result ? "Succeeded" : "Failed"));
  return result;
}

bool VirtualMemoryTracker::remove_uncommitted_region(address addr, size_t size) {
  assert(addr != nullptr, "Invalid address");
  assert(size > 0, "Invalid size");
  assert(_reserved_regions != nullptr, "Sanity check");

  ReservedMemoryRegion  rgn(addr, size);
  ReservedMemoryRegion* reserved_rgn = _reserved_regions->find(rgn);
  assert(reserved_rgn != nullptr, "No reserved region (" INTPTR_FORMAT ", " SIZE_FORMAT ")", p2i(addr), size);
  assert(reserved_rgn->contain_region(addr, size), "Not completely contained");
  const char* type_name = reserved_rgn->mem_tag_name();  // after remove, info is not complete
  bool result = reserved_rgn->remove_uncommitted_region(addr, size);
  log_debug(nmt)("Removed uncommitted region \'%s\' (" INTPTR_FORMAT ", " SIZE_FORMAT ") %s",
                 type_name,  p2i(addr), size, (result ? " Succeeded" : "Failed"));
  return result;
}

bool VirtualMemoryTracker::remove_released_region(ReservedMemoryRegion* rgn) {
  assert(rgn != nullptr, "Sanity check");
  assert(_reserved_regions != nullptr, "Sanity check");

  // uncommit regions within the released region
  ReservedMemoryRegion backup(*rgn);
  bool result = rgn->remove_uncommitted_region(rgn->base(), rgn->size());
  log_debug(nmt)("Remove uncommitted region \'%s\' (" INTPTR_FORMAT ", " SIZE_FORMAT ") %s",
                backup.mem_tag_name(), p2i(backup.base()), backup.size(), (result ? "Succeeded" : "Failed"));
  if (!result) {
    return false;
  }

  VirtualMemorySummary::record_released_memory(rgn->size(), rgn->mem_tag());
  result =  _reserved_regions->remove(*rgn);
  log_debug(nmt)("Removed region \'%s\' (" INTPTR_FORMAT ", " SIZE_FORMAT ") from _reserved_regions %s" ,
                backup.mem_tag_name(), p2i(backup.base()), backup.size(), (result ? "Succeeded" : "Failed"));
  return result;
}

bool VirtualMemoryTracker::remove_released_region(address addr, size_t size) {
  assert(addr != nullptr, "Invalid address");
  assert(size > 0, "Invalid size");
  assert(_reserved_regions != nullptr, "Sanity check");

  ReservedMemoryRegion  rgn(addr, size);
  ReservedMemoryRegion* reserved_rgn = _reserved_regions->find(rgn);

  if (reserved_rgn == nullptr) {
    log_debug(nmt)("No reserved region found for (" INTPTR_FORMAT ", " SIZE_FORMAT ")!",
                  p2i(rgn.base()), rgn.size());
  }
  assert(reserved_rgn != nullptr, "No reserved region");
  if (reserved_rgn->same_region(addr, size)) {
    return remove_released_region(reserved_rgn);
  }

  // uncommit regions within the released region
  if (!reserved_rgn->remove_uncommitted_region(addr, size)) {
    return false;
  }

  if (reserved_rgn->mem_tag() == mtClassShared) {
    if (reserved_rgn->contain_region(addr, size)) {
      // This is an unmapped CDS region, which is part of the reserved shared
      // memory region.
      // See special handling in VirtualMemoryTracker::add_reserved_region also.
      return true;
    }

    if (size > reserved_rgn->size()) {
      // This is from release the whole region spanning from archive space to class space,
      // so we release them altogether.
      ReservedMemoryRegion class_rgn(addr + reserved_rgn->size(),
                                     (size - reserved_rgn->size()));
      ReservedMemoryRegion* cls_rgn = _reserved_regions->find(class_rgn);
      assert(cls_rgn != nullptr, "Class space region  not recorded?");
      assert(cls_rgn->mem_tag() == mtClass, "Must be class mem tag");
      remove_released_region(reserved_rgn);
      remove_released_region(cls_rgn);
      return true;
    }
  }

  VirtualMemorySummary::record_released_memory(size, reserved_rgn->mem_tag());

  assert(reserved_rgn->contain_region(addr, size), "Not completely contained");
  if (reserved_rgn->base() == addr ||
      reserved_rgn->end() == addr + size) {
      reserved_rgn->exclude_region(addr, size);
    return true;
  } else {
    address top = reserved_rgn->end();
    address high_base = addr + size;
    ReservedMemoryRegion high_rgn(high_base, top - high_base,
      *reserved_rgn->call_stack(), reserved_rgn->mem_tag());

    // use original region for lower region
    reserved_rgn->exclude_region(addr, top - addr);
    LinkedListNode<ReservedMemoryRegion>* new_rgn = _reserved_regions->add(high_rgn);
    if (new_rgn == nullptr) {
      return false;
    } else {
      reserved_rgn->move_committed_regions(addr, *new_rgn->data());
      return true;
    }
  }
}

// Given an existing memory mapping registered with NMT, split the mapping in
//  two. The newly created two mappings will be registered under the call
//  stack and the memory tags of the original section.
bool VirtualMemoryTracker::split_reserved_region(address addr, size_t size, size_t split, MemTag mem_tag, MemTag split_tag) {

  ReservedMemoryRegion  rgn(addr, size);
  ReservedMemoryRegion* reserved_rgn = _reserved_regions->find(rgn);
  assert(reserved_rgn->same_region(addr, size), "Must be identical region");
  assert(reserved_rgn != nullptr, "No reserved region");
  assert(reserved_rgn->committed_size() == 0, "Splitting committed region?");

  NativeCallStack original_stack = *reserved_rgn->call_stack();
  MemTag original_tag = reserved_rgn->mem_tag();

  const char* name = reserved_rgn->mem_tag_name();
  remove_released_region(reserved_rgn);
  log_debug(nmt)("Split region \'%s\' (" INTPTR_FORMAT ", " SIZE_FORMAT ")  with size " SIZE_FORMAT,
                name, p2i(rgn.base()), rgn.size(), split);
  // Now, create two new regions.
  add_reserved_region(addr, split, original_stack, mem_tag);
  add_reserved_region(addr + split, size - split, original_stack, split_tag);

  return true;
}


// Iterate the range, find committed region within its bound.
class RegionIterator : public StackObj {
private:
  const address _start;
  const size_t  _size;

  address _current_start;
public:
  RegionIterator(address start, size_t size) :
    _start(start), _size(size), _current_start(start) {
  }

  // return true if committed region is found
  bool next_committed(address& start, size_t& size);
private:
  address end() const { return _start + _size; }
};

bool RegionIterator::next_committed(address& committed_start, size_t& committed_size) {
  if (end() <= _current_start) return false;

  const size_t page_sz = os::vm_page_size();
  const size_t current_size = end() - _current_start;
  if (os::committed_in_range(_current_start, current_size, committed_start, committed_size)) {
    assert(committed_start != nullptr, "Must be");
    assert(committed_size > 0 && is_aligned(committed_size, os::vm_page_size()), "Must be");

    _current_start = committed_start + committed_size;
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
    assert_lock_strong(NmtVirtualMemory_lock);
    if (rgn->mem_tag() == mtThreadStack) {
      address stack_bottom = rgn->thread_stack_uncommitted_bottom();
      address committed_start;
      size_t  committed_size;
      size_t stack_size = rgn->base() + rgn->size() - stack_bottom;
      // Align the size to work with full pages (Alpine and AIX stack top is not page aligned)
      size_t aligned_stack_size = align_up(stack_size, os::vm_page_size());

      ReservedMemoryRegion* region = const_cast<ReservedMemoryRegion*>(rgn);
      NativeCallStack ncs; // empty stack

      RegionIterator itr(stack_bottom, aligned_stack_size);
      DEBUG_ONLY(bool found_stack = false;)
      while (itr.next_committed(committed_start, committed_size)) {
        assert(committed_start != nullptr, "Should not be null");
        assert(committed_size > 0, "Should not be 0");
        // unaligned stack_size case: correct the region to fit the actual stack_size
        if (stack_bottom + stack_size < committed_start + committed_size) {
          committed_size = stack_bottom + stack_size - committed_start;
        }
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
  assert(_reserved_regions != nullptr, "Sanity check");
  NmtVirtualMemoryLocker ml;
  // Check that the _reserved_regions haven't been deleted.
  if (_reserved_regions != nullptr) {
    LinkedListNode<ReservedMemoryRegion>* head = _reserved_regions->head();
    while (head != nullptr) {
      const ReservedMemoryRegion* rgn = head->peek();
      if (!walker->do_allocation_site(rgn)) {
        return false;
      }
      head = head->next();
    }
   }
  return true;
}

class PrintRegionWalker : public VirtualMemoryWalker {
private:
  const address               _p;
  outputStream*               _st;
  NativeCallStackPrinter      _stackprinter;
public:
  PrintRegionWalker(const void* p, outputStream* st) :
    _p((address)p), _st(st), _stackprinter(st) { }

  bool do_allocation_site(const ReservedMemoryRegion* rgn) {
    if (rgn->contain_address(_p)) {
      _st->print_cr(PTR_FORMAT " in mmap'd memory region [" PTR_FORMAT " - " PTR_FORMAT "], tag %s",
        p2i(_p), p2i(rgn->base()), p2i(rgn->base() + rgn->size()), NMTUtil::tag_to_enum_name(rgn->mem_tag()));
      if (MemTracker::tracking_level() == NMT_detail) {
        _stackprinter.print_stack(rgn->call_stack());
        _st->cr();
      }
      return false;
    }
    return true;
  }
};

// If p is contained within a known memory region, print information about it to the
// given stream and return true; false otherwise.
bool VirtualMemoryTracker::print_containing_region(const void* p, outputStream* st) {
  PrintRegionWalker walker(p, st);
  return !walk_virtual_memory(&walker);

}
