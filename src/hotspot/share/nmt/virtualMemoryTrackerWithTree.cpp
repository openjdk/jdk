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

RegionsTree* VirtualMemoryTrackerWithTree::_tree;

bool VirtualMemoryTrackerWithTree::initialize(NMT_TrackingLevel level) {
  assert(_tree == nullptr, "only call once");
  if (level >= NMT_summary) {
    _tree = new (std::nothrow, mtNMT) RegionsTree(level == NMT_detail);
    return (_tree != nullptr);
  }
  return true;
}


bool VirtualMemoryTrackerWithTree::add_reserved_region(address base_addr, size_t size,
  const NativeCallStack& stack, MEMFLAGS flag) {
  assert(_tree != nullptr, "Sanity check");
  if (flag == mtTest) {
    log_debug(nmt)("add reserve rgn, base: " INTPTR_FORMAT " end: " INTPTR_FORMAT, p2i(base_addr), p2i(base_addr + size));
  }
  VMATree::SummaryDiff diff = _tree->reserve_mapping((size_t)base_addr, size, _tree->make_region_data(stack, flag));
  apply_summary_diff(diff);
  return true;

}

void VirtualMemoryTrackerWithTree::set_reserved_region_type(address addr, MEMFLAGS flag) {
    ReservedMemoryRegion rgn;
    _tree->find_reserved_region(addr, &rgn);
    if (rgn.flag() == flag)
      return;

    const VMATree::position& start = (VMATree::position)addr;
    const VMATree::position& end = (VMATree::position)(rgn.end() + 1);
    RegionsTree::NodeHelper* prev = nullptr;
    if (start > end) {
      _tree->dump(tty);
      tty->print_cr("requested addr: " INTPTR_FORMAT " end: " INTPTR_FORMAT, p2i(addr), p2i((address) end));
      _tree->find_reserved_region(addr, &rgn, true);
    }
    size_t rgn_size = 0;
    size_t comm_size = 0;
    MEMFLAGS old_flag = mtNone, bak_out_flag;
    bool base_flag_set = false;
    bool release_node_found = false;
    _tree->visit_range_in_order(start, end, [&](VMATree::TreapNode* node){
      RegionsTree::NodeHelper* curr = (RegionsTree::NodeHelper*)node;
      if (!base_flag_set) {
        old_flag = curr->out_flag();
        base_flag_set = true;
      }
      bak_out_flag = curr->out_flag();
      curr->set_out_flag(flag);
      if (prev != nullptr) {
        curr->set_in_flag(flag);
        rgn_size += curr->distance_from(prev);
        if (prev->is_committed_begin())
          comm_size += curr->distance_from(prev);
      }
      prev = curr;
      if (curr->is_released_begin() || bak_out_flag != old_flag) {
        if (bak_out_flag != old_flag) {
          curr->set_out_flag(bak_out_flag);
        }
        VirtualMemorySummary::move_reserved_memory(old_flag, flag, rgn_size);
        VirtualMemorySummary::move_committed_memory(old_flag, flag, comm_size);
        release_node_found = true;
        return false;
      }
      return true;
    });
}

void VirtualMemoryTrackerWithTree::apply_summary_diff(VMATree::SummaryDiff diff) {
  for (int i = 0; i < mt_number_of_types; i++) {
    auto r = diff.flag[i].reserve;
    auto c = diff.flag[i].commit;
    MEMFLAGS flag = NMTUtil::index_to_flag(i);
    size_t reserved = VirtualMemorySummary::as_snapshot()->by_type(flag)->reserved();
    size_t committed = VirtualMemorySummary::as_snapshot()->by_type(flag)->committed();
    auto print_err = [&](const char* str) {
      log_debug(nmt)("summary mismatch, at %s, for %s,"
                      " diff-reserved: " SSIZE_FORMAT
                      " diff-committed: " SSIZE_FORMAT
                      " vms-reserved: "  SIZE_FORMAT
                      " vms-committed: " SIZE_FORMAT,
                      str, NMTUtil::flag_to_name(flag), r, c, reserved, committed);
    };
    if (r != 0) {
      if (r > 0)
        VirtualMemorySummary::record_reserved_memory(r, flag);
      else {
        if ((size_t)-r <= reserved)
          VirtualMemorySummary::record_released_memory(-r, flag);
        else
          print_err("release");
      }
    }
    if (c != 0) {
      if (c > 0) {
        if ((size_t)c <= reserved)
          VirtualMemorySummary::record_committed_memory(c, flag);
        else
          print_err("commit");
      }
      else {
        if ((size_t)-c <= reserved && (size_t)-c <= committed)
          VirtualMemorySummary::record_uncommitted_memory(-c, flag);
        else
          print_err("uncommit");
      }
    }
  }
}

bool VirtualMemoryTrackerWithTree::add_committed_region(address addr, size_t size,
  const NativeCallStack& stack) {
    VMATree::SummaryDiff diff = _tree->commit_region(addr, size, stack);
    apply_summary_diff(diff);
    return true;
}

bool VirtualMemoryTrackerWithTree::remove_uncommitted_region(address addr, size_t size) {
  ThreadCritical tc;
  VMATree::SummaryDiff diff = _tree->uncommit_region(addr, size);
  apply_summary_diff(diff);
  return true;
}

bool VirtualMemoryTrackerWithTree::remove_released_region(address addr, size_t size) {
  VMATree::SummaryDiff diff = _tree->release_mapping((VMATree::position)addr, size);
  apply_summary_diff(diff);
  return true;

}

// Given an existing memory mapping registered with NMT, split the mapping in
//  two. The newly created two mappings will be registered under the call
//  stack and the memory flags of the original section.
bool VirtualMemoryTrackerWithTree::split_reserved_region(address addr, size_t size, size_t split, MEMFLAGS flag, MEMFLAGS split_flag) {
  add_reserved_region(addr, split, NativeCallStack::empty_stack(), flag);
  add_reserved_region(addr + split, size - split, NativeCallStack::empty_stack(), split_flag);
  return true;
}

bool VirtualMemoryTrackerWithTree::print_containing_region(const void* p, outputStream* st) {
  ReservedMemoryRegion rmr;
  _tree->find_reserved_region((address)p, &rmr);
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

bool VirtualMemoryTrackerWithTree::walk_virtual_memory(VirtualMemoryWalker* walker) {
  ReservedMemoryRegion rmr;
  ThreadCritical tc;
  _tree->visit_reserved_regions(&rmr, [&](ReservedMemoryRegion* rgn) {
    log_info(nmt)("region in walker vmem, base: " INTPTR_FORMAT " size: " SIZE_FORMAT " , %s", p2i(rgn->base()), rgn->size(), rgn->flag_name());
    if (!walker->do_allocation_site(rgn))
      return false;
    return true;
  });
  return true;
}
