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

size_t ReservedMemoryRegion::committed_size() const {
  size_t committed = 0;
  size_t result = 0;
  VirtualMemoryTracker::Instance::tree()->visit_committed_regions((VMATree::position)base(), size(), [&](CommittedMemoryRegion& crgn) {
    result += crgn.size();
    return true;
  });
  return result;
}

address ReservedMemoryRegion::thread_stack_uncommitted_bottom() const {
  address bottom = base();
  address top = base() + size();
  VirtualMemoryTracker::Instance::tree()->visit_committed_regions((VMATree::position)base(), size(), [&](CommittedMemoryRegion& crgn) {
    address committed_top = crgn.base() + crgn.size();
    if (committed_top < top) {
      // committed stack guard pages, skip them
      bottom = crgn.base() + crgn.size();
    } else {
      assert(top == committed_top, "Sanity, top=" INTPTR_FORMAT " , com-top=" INTPTR_FORMAT, p2i(top), p2i(committed_top));
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
        VirtualMemoryTracker::Instance::add_committed_region(committed_start, committed_size, ncs);
        log_warning(cds)("st start: " INTPTR_FORMAT " size: " SIZE_FORMAT, p2i(committed_start), committed_size);
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

void VirtualMemoryTracker::Instance::snapshot_thread_stacks() {
  SnapshotThreadStackWalker walker;
  walk_virtual_memory(&walker);
}

