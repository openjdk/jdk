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
#include "nmt/vmtCommon.hpp"
#include "runtime/os.hpp"
#include "runtime/threadCritical.hpp"
#include "utilities/ostream.hpp"


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
  size_t result = 0;
  VirtualMemoryTracker::Instance::tree()->visit_committed_regions(*this, [&](CommittedMemoryRegion& crgn) {
    result += crgn.size();
    return true;
  });
  return result;
}

void ReservedMemoryRegion::set_tag(MemTag mt) {
  assert((mem_tag() == mtNone || mem_tag() == mt),
         "Overwrite memory tag for region [" INTPTR_FORMAT "-" INTPTR_FORMAT "), %u->%u.",
         p2i(base()), p2i(end()), (unsigned)mem_tag(), (unsigned)mt);
  if (mem_tag() != mt) {
    VirtualMemorySummary::move_reserved_memory(mem_tag(), mt, size());
    VirtualMemorySummary::move_committed_memory(mem_tag(), mt, committed_size());
    _mem_tag = mt;
  }
}

address ReservedMemoryRegion::thread_stack_uncommitted_bottom() const {
  address bottom = base();
  address top = base() + size();
  VirtualMemoryTracker::Instance::tree()->visit_committed_regions(*this, [&](CommittedMemoryRegion& crgn) {
    address committed_top = crgn.base() + crgn.size();
    if (committed_top < top) {
      // committed stack guard pages, skip them
      bottom = crgn.base() + crgn.size();
    } else {
      assert(top == committed_top, "Sanity");
      return false;;
    }
    return true;
  });

  return bottom;
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


void VirtualMemoryTracker::Instance::snapshot_thread_stacks() {
  SnapshotThreadStackWalker walker;
  walk_virtual_memory(&walker);
}

