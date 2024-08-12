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
#include "nmt/virtualMemoryTrackerWithTree.hpp"
#include "nmt/memTracker.hpp"

VirtualMemoryTrackerWithTree* VirtualMemoryTrackerWithTree::Instance::_tracker = nullptr;

bool VirtualMemoryTrackerWithTree::Instance::initialize(NMT_TrackingLevel level) {
  assert(_tracker == nullptr, "only call once");
  if (level >= NMT_summary) {
    _tracker = static_cast<VirtualMemoryTrackerWithTree*>(os::malloc(sizeof(VirtualMemoryTrackerWithTree), mtNMT));
    if (_tracker == nullptr) return false;
    new (_tracker) VirtualMemoryTrackerWithTree(level == NMT_detail);
    return _tracker->tree() != nullptr;
  }
  return true;
}


bool VirtualMemoryTrackerWithTree::Instance::add_reserved_region(address base_addr, size_t size,
  const NativeCallStack& stack, MEMFLAGS flag) {
    return _tracker->add_reserved_region(base_addr, size, stack, flag);
}

bool VirtualMemoryTrackerWithTree::add_reserved_region(address base_addr, size_t size,
  const NativeCallStack& stack, MEMFLAGS flag) {
  if (flag == mtTest) {
    log_debug(nmt)("add reserve rgn, base: " INTPTR_FORMAT " end: " INTPTR_FORMAT, p2i(base_addr), p2i(base_addr + size));
  }
  VMATree::SummaryDiff diff = tree()->reserve_mapping((size_t)base_addr, size, tree()->make_region_data(stack, flag));
  apply_summary_diff(diff);
  return true;

}

void VirtualMemoryTrackerWithTree::Instance::set_reserved_region_type(address addr, MEMFLAGS flag) {
  _tracker->set_reserved_region_type(addr, flag);
}

void VirtualMemoryTrackerWithTree::set_reserved_region_type(address addr, MEMFLAGS flag) {
    ReservedMemoryRegion rgn = tree()->find_reserved_region(addr);
    if (rgn.flag() == flag)
      return;

    const VMATree::position& start = (VMATree::position)addr;
    const VMATree::position& end = (VMATree::position)(rgn.end() + 1);
    RegionsTree::NodeHelper prev;
    if (start > end) {
      tree()->dump(tty);
      tty->print_cr("requested addr: " INTPTR_FORMAT " end: " INTPTR_FORMAT, p2i(addr), p2i((address) end));
      rgn = tree()->find_reserved_region(addr, true);
    }
    size_t rgn_size = 0;
    size_t comm_size = 0;
    MEMFLAGS old_flag = mtNone, bak_out_flag;
    bool base_flag_set = false;
    bool release_node_found = false;
    tree()->visit_range_in_order(start, end, [&](VMATree::TreapNode* node){
      RegionsTree::NodeHelper curr(node);
      if (!base_flag_set) {
        old_flag = curr.out_flag();
        base_flag_set = true;
      }
      bak_out_flag = curr.out_flag();
      curr.set_out_flag(flag);
      if (prev.is_valid()) {
        curr.set_in_flag(flag);
        rgn_size += curr.distance_from(prev);
        if (prev.is_committed_begin())
          comm_size += curr.distance_from(prev);
      }
      prev = curr;
      if (curr.is_released_begin() || bak_out_flag != old_flag) {
        if (bak_out_flag != old_flag) {
          curr.set_out_flag(bak_out_flag);
        }
        VirtualMemorySummary::move_reserved_memory(old_flag, flag, rgn_size);
        VirtualMemorySummary::move_committed_memory(old_flag, flag, comm_size);
        release_node_found = true;
        return false;
      }
      return true;
    });
}

void VirtualMemoryTrackerWithTree::Instance::apply_summary_diff(VMATree::SummaryDiff diff) {
  _tracker->apply_summary_diff(diff);
}

void VirtualMemoryTrackerWithTree::apply_summary_diff(VMATree::SummaryDiff diff) {
  VMATree::SingleDiff::delta reserve_delta, commit_delta;
  size_t reserved, committed;
  MEMFLAGS flag = mtNone;
  auto print_err = [&](const char* str) {
    log_debug(nmt)("summary mismatch, at %s, for %s,"
                    " diff-reserved: " SSIZE_FORMAT
                    " diff-committed: " SSIZE_FORMAT
                    " vms-reserved: "  SIZE_FORMAT
                    " vms-committed: " SIZE_FORMAT,
                    str, NMTUtil::flag_to_name(flag), (ssize_t)reserve_delta, (ssize_t)commit_delta, reserved, committed);
  };
  for (int i = 0; i < mt_number_of_types; i++) {
    reserve_delta = diff.flag[i].reserve;
    commit_delta = diff.flag[i].commit;
    flag = NMTUtil::index_to_flag(i);
    reserved = VirtualMemorySummary::as_snapshot()->by_type(flag)->reserved();
    committed = VirtualMemorySummary::as_snapshot()->by_type(flag)->committed();
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

bool VirtualMemoryTrackerWithTree::Instance::add_committed_region(address addr, size_t size,
  const NativeCallStack& stack) {
  return _tracker->add_committed_region(addr, size, stack);
}

bool VirtualMemoryTrackerWithTree::add_committed_region(address addr, size_t size,
  const NativeCallStack& stack) {
    VMATree::SummaryDiff diff = tree()->commit_region(addr, size, stack);
    apply_summary_diff(diff);
    return true;
}

bool VirtualMemoryTrackerWithTree::Instance::remove_uncommitted_region(address addr, size_t size) {
  return _tracker->remove_uncommitted_region(addr, size);
}

bool VirtualMemoryTrackerWithTree::remove_uncommitted_region(address addr, size_t size) {
  ThreadCritical tc;
  VMATree::SummaryDiff diff = tree()->uncommit_region(addr, size);
  apply_summary_diff(diff);
  return true;
}

bool VirtualMemoryTrackerWithTree::Instance::remove_released_region(address addr, size_t size) {
  return _tracker->remove_released_region(addr, size);
}

bool VirtualMemoryTrackerWithTree::remove_released_region(address addr, size_t size) {
  VMATree::SummaryDiff diff = tree()->release_mapping((VMATree::position)addr, size);
  apply_summary_diff(diff);
  return true;

}

bool VirtualMemoryTrackerWithTree::Instance::split_reserved_region(address addr, size_t size, size_t split, MEMFLAGS flag, MEMFLAGS split_flag) {
  return _tracker->split_reserved_region(addr, size, split, flag, split_flag);
}

bool VirtualMemoryTrackerWithTree::split_reserved_region(address addr, size_t size, size_t split, MEMFLAGS flag, MEMFLAGS split_flag) {
  add_reserved_region(addr, split, NativeCallStack::empty_stack(), flag);
  add_reserved_region(addr + split, size - split, NativeCallStack::empty_stack(), split_flag);
  return true;
}

bool VirtualMemoryTrackerWithTree::Instance::print_containing_region(const void* p, outputStream* st) {
  return _tracker->print_containing_region(p, st);
}

bool VirtualMemoryTrackerWithTree::print_containing_region(const void* p, outputStream* st) {
  ReservedMemoryRegion rmr = tree()->find_reserved_region((address)p);
  log_debug(nmt)("containing rgn: base=" INTPTR_FORMAT, p2i(rmr.base()));
  if (!rmr.contain_address((address)p))
    return false;
  st->print_cr(PTR_FORMAT " in mmap'd memory region [" PTR_FORMAT " - " PTR_FORMAT "], tag %s",
               p2i(p), p2i(rmr.base()), p2i(rmr.end()), NMTUtil::flag_to_enum_name(rmr.flag()));
  if (MemTracker::tracking_level() == NMT_detail) {
    rmr.call_stack()->print_on(st);
  }
  st->cr();
  return true;
}

bool VirtualMemoryTrackerWithTree::Instance::walk_virtual_memory(VirtualMemoryWalker* walker) {
  return _tracker->walk_virtual_memory(walker);
}

bool VirtualMemoryTrackerWithTree::walk_virtual_memory(VirtualMemoryWalker* walker) {
  ThreadCritical tc;
  tree()->visit_reserved_regions([&](ReservedMemoryRegion& rgn) {
    log_info(nmt)("region in walker vmem, base: " INTPTR_FORMAT " size: " SIZE_FORMAT " , %s", p2i(rgn.base()), rgn.size(), rgn.flag_name());
    if (!walker->do_allocation_site(&rgn))
      return false;
    return true;
  });
  return true;
}
