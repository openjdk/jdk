/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "logging/log.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "nmt/regionsTree.hpp"
#include "nmt/regionsTree.inline.hpp"
#include "runtime/os.hpp"
#include "utilities/ostream.hpp"

VirtualMemoryTracker* VirtualMemoryTracker::Instance::_tracker = nullptr;
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
  VirtualMemoryTracker::Instance::snapshot_thread_stacks();
  as_snapshot()->copy_to(s);
}

bool VirtualMemoryTracker::Instance::initialize(NMT_TrackingLevel level) {
  assert(_tracker == nullptr, "only call once");
  if (level >= NMT_summary) {
    void* tracker = os::malloc(sizeof(VirtualMemoryTracker), mtNMT);
    if (tracker == nullptr) return false;
    _tracker = new (tracker) VirtualMemoryTracker(level == NMT_detail);
  }
  return true;
}


void VirtualMemoryTracker::Instance::add_reserved_region(address base_addr, size_t size,
  const NativeCallStack& stack, MemTag mem_tag) {
    assert(_tracker != nullptr, "Sanity check");
    _tracker->add_reserved_region(base_addr, size, stack, mem_tag);
}

void VirtualMemoryTracker::add_reserved_region(address base_addr, size_t size,
  const NativeCallStack& stack, MemTag mem_tag) {
  VMATree::SummaryDiff diff = tree()->reserve_mapping((size_t)base_addr, size, tree()->make_region_data(stack, mem_tag));
  apply_summary_diff(diff);
}

void VirtualMemoryTracker::Instance::set_reserved_region_tag(address addr, size_t size, MemTag mem_tag) {
  assert(_tracker != nullptr, "Sanity check");
  _tracker->set_reserved_region_tag(addr, size, mem_tag);
}

void VirtualMemoryTracker::set_reserved_region_tag(address addr, size_t size, MemTag mem_tag) {
    VMATree::SummaryDiff diff = tree()->set_tag((VMATree::position) addr, size, mem_tag);
    apply_summary_diff(diff);
}

void VirtualMemoryTracker::Instance::apply_summary_diff(VMATree::SummaryDiff diff) {
  assert(_tracker != nullptr, "Sanity check");
  _tracker->apply_summary_diff(diff);
}

void VirtualMemoryTracker::apply_summary_diff(VMATree::SummaryDiff diff) {
  VMATree::SingleDiff::delta reserve_delta, commit_delta;
  size_t reserved, committed;
  MemTag tag = mtNone;
  auto print_err = [&](const char* str) {
#ifdef ASSERT
    log_error(nmt)("summary mismatch, at %s, for %s,"
                   " diff-reserved:  %ld"
                   " diff-committed: %ld"
                   " vms-reserved: %zu"
                   " vms-committed: %zu",
                   str, NMTUtil::tag_to_name(tag), (long)reserve_delta, (long)commit_delta, reserved, committed);
#endif
  };

  for (int i = 0; i < mt_number_of_tags; i++) {
    reserve_delta = diff.tag[i].reserve;
    commit_delta = diff.tag[i].commit;
    tag = NMTUtil::index_to_tag(i);
    reserved = VirtualMemorySummary::as_snapshot()->by_tag(tag)->reserved();
    committed = VirtualMemorySummary::as_snapshot()->by_tag(tag)->committed();
    if (reserve_delta != 0) {
      if (reserve_delta > 0) {
        VirtualMemorySummary::record_reserved_memory(reserve_delta, tag);
      } else {
        if ((size_t)-reserve_delta <= reserved) {
          VirtualMemorySummary::record_released_memory(-reserve_delta, tag);
        } else {
          print_err("release");
        }
      }
    }
    if (commit_delta != 0) {
      if (commit_delta > 0) {
        if ((size_t)commit_delta <= ((size_t)reserve_delta + reserved)) {
          VirtualMemorySummary::record_committed_memory(commit_delta, tag);
        }
        else {
          print_err("commit");
        }
      }
      else {
        if ((size_t)-commit_delta <= committed) {
          VirtualMemorySummary::record_uncommitted_memory(-commit_delta, tag);
        } else {
          print_err("uncommit");
        }
      }
    }
  }
}

void VirtualMemoryTracker::Instance::add_committed_region(address addr, size_t size,
  const NativeCallStack& stack) {
  assert(_tracker != nullptr, "Sanity check");
  _tracker->add_committed_region(addr, size, stack);
}

void VirtualMemoryTracker::add_committed_region(address addr, size_t size,
  const NativeCallStack& stack) {
    VMATree::SummaryDiff diff = tree()->commit_region(addr, size, stack);
    apply_summary_diff(diff);
}

void VirtualMemoryTracker::Instance::remove_uncommitted_region(address addr, size_t size) {
  assert(_tracker != nullptr, "Sanity check");
  _tracker->remove_uncommitted_region(addr, size);
}

void VirtualMemoryTracker::remove_uncommitted_region(address addr, size_t size) {
  MemTracker::assert_locked();
  VMATree::SummaryDiff diff = tree()->uncommit_region(addr, size);
  apply_summary_diff(diff);
}

void VirtualMemoryTracker::Instance::remove_released_region(address addr, size_t size) {
  assert(_tracker != nullptr, "Sanity check");
  _tracker->remove_released_region(addr, size);
}

void VirtualMemoryTracker::remove_released_region(address addr, size_t size) {
  VMATree::SummaryDiff diff = tree()->release_mapping((VMATree::position)addr, size);
  apply_summary_diff(diff);
}

void VirtualMemoryTracker::Instance::split_reserved_region(address addr, size_t size, size_t split, MemTag mem_tag, MemTag split_mem_tag) {
  assert(_tracker != nullptr, "Sanity check");
  _tracker->split_reserved_region(addr, size, split, mem_tag, split_mem_tag);
}

void VirtualMemoryTracker::split_reserved_region(address addr, size_t size, size_t split, MemTag mem_tag, MemTag split_mem_tag) {
  add_reserved_region(addr, split, NativeCallStack::empty_stack(), mem_tag);
  add_reserved_region(addr + split, size - split, NativeCallStack::empty_stack(), split_mem_tag);
}

bool VirtualMemoryTracker::Instance::print_containing_region(const void* p, outputStream* st) {
  assert(_tracker != nullptr, "Sanity check");
  return _tracker->print_containing_region(p, st);
}

bool VirtualMemoryTracker::print_containing_region(const void* p, outputStream* st) {
  ReservedMemoryRegion rmr = tree()->find_reserved_region((address)p);
  if (!rmr.contain_address((address)p)) {
    return false;
  }
  st->print_cr(PTR_FORMAT " in mmap'd memory region [" PTR_FORMAT " - " PTR_FORMAT "], tag %s",
               p2i(p), p2i(rmr.base()), p2i(rmr.end()), NMTUtil::tag_to_enum_name(rmr.mem_tag()));
  if (MemTracker::tracking_level() == NMT_detail) {
    rmr.call_stack()->print_on(st);
  }
  st->cr();
  return true;
}

bool VirtualMemoryTracker::Instance::walk_virtual_memory(VirtualMemoryWalker* walker) {
  assert(_tracker != nullptr, "Sanity check");
  return _tracker->walk_virtual_memory(walker);
}

bool VirtualMemoryTracker::walk_virtual_memory(VirtualMemoryWalker* walker) {
  MemTracker::NmtVirtualMemoryLocker nvml;
  tree()->visit_reserved_regions([&](ReservedMemoryRegion& rgn) {
    if (!walker->do_allocation_site(&rgn)) {
      return false;
    }
    return true;
  });
  return true;
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

address ReservedMemoryRegion::thread_stack_uncommitted_bottom() const {
  address bottom = base();
  address top = base() + size();
  VirtualMemoryTracker::Instance::tree()->visit_committed_regions(*this, [&](CommittedMemoryRegion& crgn) {
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
    if (MemTracker::NmtVirtualMemoryLocker::is_safe_to_use()) {
      assert_lock_strong(NmtVirtualMemory_lock);
    }
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

ReservedMemoryRegion RegionsTree::find_reserved_region(address addr) {
    ReservedMemoryRegion rmr;
    auto contain_region = [&](ReservedMemoryRegion& region_in_tree) {
      if (region_in_tree.contain_address(addr)) {
        rmr = region_in_tree;
        return false;
      }
      return true;
    };
    visit_reserved_regions(contain_region);
    return rmr;
}

bool CommittedMemoryRegion::equals(const ReservedMemoryRegion& rmr) const {
  return size() == rmr.size() && call_stack()->equals(*(rmr.call_stack()));
}