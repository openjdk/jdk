/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "nmt/virtualMemoryTracker.hpp"
#include "nmt/memTracker.hpp"

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
    _tracker = static_cast<VirtualMemoryTracker*>(os::malloc(sizeof(VirtualMemoryTracker), mtNMT));
    if (_tracker == nullptr) return false;
    new (_tracker) VirtualMemoryTracker(level == NMT_detail);
    return _tracker->tree() != nullptr;
  }
  return true;
}


bool VirtualMemoryTracker::Instance::add_reserved_region(address base_addr, size_t size,
  const NativeCallStack& stack, MemTag flag) {
    assert(_tracker != nullptr, "Sanity check");
    return _tracker->add_reserved_region(base_addr, size, stack, flag);
}

bool VirtualMemoryTracker::add_reserved_region(address base_addr, size_t size,
  const NativeCallStack& stack, MemTag flag) {
  if (flag == mtTest) {
    log_debug(nmt)("add reserve rgn, base: " INTPTR_FORMAT " end: " INTPTR_FORMAT, p2i(base_addr), p2i(base_addr + size));
  }
  VMATree::SummaryDiff diff = tree()->reserve_mapping((size_t)base_addr, size, tree()->make_region_data(stack, flag));
  apply_summary_diff(diff);
  return true;

}

void VirtualMemoryTracker::Instance::set_reserved_region_tag(address addr, size_t size, MemTag mem_tag) {
  assert(_tracker != nullptr, "Sanity check");
  _tracker->set_reserved_region_tag(addr, size, mem_tag);
}

void VirtualMemoryTracker::set_reserved_region_tag(address addr, size_t size, MemTag mem_tag) {
    VMATree::RegionData rd(NativeCallStackStorage::StackIndex(), mem_tag);
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
  MemTag flag = mtNone;
  auto print_err = [&](const char* str) {
    log_debug(nmt)("summary mismatch, at %s, for %s,"
                    " diff-reserved: " SSIZE_FORMAT
                    " diff-committed: " SSIZE_FORMAT
                    " vms-reserved: "  SIZE_FORMAT
                    " vms-committed: " SIZE_FORMAT,
                    str, NMTUtil::tag_to_name(flag), (ssize_t)reserve_delta, (ssize_t)commit_delta, reserved, committed);
  };
  for (int i = 0; i < mt_number_of_tags; i++) {
    reserve_delta = diff.tag[i].reserve;
    commit_delta = diff.tag[i].commit;
    flag = NMTUtil::index_to_tag(i);
    reserved = VirtualMemorySummary::as_snapshot()->by_tag(flag)->reserved();
    committed = VirtualMemorySummary::as_snapshot()->by_tag(flag)->committed();
    if (reserve_delta != 0) {
      if (reserve_delta > 0)
        VirtualMemorySummary::record_reserved_memory(reserve_delta, flag);
      else {
        if ((size_t)-reserve_delta <= reserved)
          VirtualMemorySummary::record_released_memory(-reserve_delta, flag);
        else
          print_err("release");
      }
    }
    if (commit_delta != 0) {
      if (commit_delta > 0) {
        if ((size_t)commit_delta <= reserved)
          VirtualMemorySummary::record_committed_memory(commit_delta, flag);
        else
          print_err("commit");
      }
      else {
        if ((size_t)-commit_delta <= reserved && (size_t)-commit_delta <= committed)
          VirtualMemorySummary::record_uncommitted_memory(-commit_delta, flag);
        else
          print_err("uncommit");
      }
    }
  }
}

bool VirtualMemoryTracker::Instance::add_committed_region(address addr, size_t size,
  const NativeCallStack& stack) {
  assert(_tracker != nullptr, "Sanity check");
  return _tracker->add_committed_region(addr, size, stack);
}

bool VirtualMemoryTracker::add_committed_region(address addr, size_t size,
  const NativeCallStack& stack) {
    VMATree::SummaryDiff diff = tree()->commit_region(addr, size, stack);
    apply_summary_diff(diff);
    return true;
}

bool VirtualMemoryTracker::Instance::remove_uncommitted_region(address addr, size_t size) {
  assert(_tracker != nullptr, "Sanity check");
  return _tracker->remove_uncommitted_region(addr, size);
}

bool VirtualMemoryTracker::remove_uncommitted_region(address addr, size_t size) {
  ThreadCritical tc;
  VMATree::SummaryDiff diff = tree()->uncommit_region(addr, size);
  apply_summary_diff(diff);
  return true;
}

bool VirtualMemoryTracker::Instance::remove_released_region(address addr, size_t size) {
  assert(_tracker != nullptr, "Sanity check");
  return _tracker->remove_released_region(addr, size);
}

bool VirtualMemoryTracker::remove_released_region(address addr, size_t size) {
  VMATree::SummaryDiff diff = tree()->release_mapping((VMATree::position)addr, size);
  apply_summary_diff(diff);
  return true;

}

bool VirtualMemoryTracker::Instance::split_reserved_region(address addr, size_t size, size_t split, MemTag flag, MemTag split_flag) {
  assert(_tracker != nullptr, "Sanity check");
  return _tracker->split_reserved_region(addr, size, split, flag, split_flag);
}

bool VirtualMemoryTracker::split_reserved_region(address addr, size_t size, size_t split, MemTag flag, MemTag split_flag) {
  add_reserved_region(addr, split, NativeCallStack::empty_stack(), flag);
  add_reserved_region(addr + split, size - split, NativeCallStack::empty_stack(), split_flag);
  return true;
}

bool VirtualMemoryTracker::Instance::print_containing_region(const void* p, outputStream* st) {
  assert(_tracker != nullptr, "Sanity check");
  return _tracker->print_containing_region(p, st);
}

bool VirtualMemoryTracker::print_containing_region(const void* p, outputStream* st) {
  ReservedMemoryRegion rmr = tree()->find_reserved_region((address)p);
  log_debug(nmt)("containing rgn: base=" INTPTR_FORMAT, p2i(rmr.base()));
  if (!rmr.contain_address((address)p))
    return false;
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
  ThreadCritical tc;
  tree()->visit_reserved_regions([&](ReservedMemoryRegion& rgn) {
    log_info(nmt)("region in walker vmem, base: " INTPTR_FORMAT " size: " SIZE_FORMAT " , %s", p2i(rgn.base()), rgn.size(), rgn.tag_name());
    if (!walker->do_allocation_site(&rgn))
      return false;
    return true;
  });
  return true;
}
