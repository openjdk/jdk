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
#include "memory/allocation.hpp"
#include "nmt/memflags.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/nmtNativeCallStackStorage.hpp"
#include "nmt/regionsTree.hpp"
#include "nmt/virtualMemoryTracker.hpp"
#include "nmt/virtualMemoryTrackerWithTree.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"
#include "threadHelper.inline.hpp"
#include "unittest.hpp"

using Tree = VMATree;
using Node = Tree::TreapNode;
using NCS = NativeCallStackStorage;

address operator ""_a(unsigned long long x) { return (address) x;}

class VMTWithVMATreeTest : public testing::Test {
 public:
  const int region_size = 100 * K;
  const int commit_size = 4 * K;
  const int region_gap = 4 * K;
  const address all_base = 0xABCD0000_a;

  VMTWithVMATreeTest() { }
  class TimeIt {
    size_t* _var;
    double start_time;
    const int TIME_SCALE = 1000000000;
   public:
    TimeIt(size_t* var) : _var(var) {
      start_time = os::elapsedTime();
    }
    ~TimeIt() {
      *_var += (size_t)((os::elapsedTime() - start_time) * TIME_SCALE);
    }
  };
  typedef struct {
    size_t reserve, set_type, commit, uncommit, release;
  } VMTPerfData;

  VMTPerfData perf_data[2];


  address region_address(int r, int c = 0) {
    address region_base = all_base + r * (region_size + region_gap);
    return region_base + c * commit_size;
  }

  void vmt_cycle() {
    const int region_count = 40;
    const int commit_count = region_size / commit_size;
    const int uncommit_count = commit_count;
    address region_base;

    int i = MemTracker::is_using_sorted_link_list() ? 0 : 1;

    perf_data[i].reserve  =
    perf_data[i].set_type =
    perf_data[i].commit   =
    perf_data[i].uncommit =
    perf_data[i].release  =  0;

    for (int rgn_no = 0; rgn_no < region_count; rgn_no++) {
      {
        TimeIt timer(&perf_data[i].reserve);
        region_base = region_address(rgn_no);
        MemTracker::record_virtual_memory_reserve(region_base, region_size, CALLER_PC);
      }
    }
    for (int rgn_no = 0; rgn_no < region_count; rgn_no++) {
      {
        TimeIt timer(&perf_data[i].set_type);
        region_base = region_address(rgn_no);
        MemTracker::record_virtual_memory_type(region_base, mtTest);
      }
    }

    for (int rgn_no = 0; rgn_no < region_count; rgn_no++) {
      for (int c = 1; c < commit_count; c += 4) {
        {
          TimeIt timer(&perf_data[i].commit);
          address commit_addr = region_address(rgn_no, c);
          MemTracker::record_virtual_memory_commit(commit_addr, commit_size, CALLER_PC);
        }
      }
      for (int c = 1; c < uncommit_count; c += 4) {
        {
          ThreadCritical tc;
          TimeIt timer(&perf_data[i].uncommit);
          address commit_addr = region_address(rgn_no, c);
          MemTracker::record_virtual_memory_uncommit(commit_addr, commit_size);
        }
      }
    }
    for (int rgn_no = 0; rgn_no < region_count; rgn_no++) {
      {
        ThreadCritical tc;
        TimeIt timer(&perf_data[i].release);
        region_base = region_address(rgn_no);
        MemTracker::record_virtual_memory_release(region_base, region_size);
      }
    }
  }

  void compare_and_report_perf_data() {
    const char* common_str =  "\n***** Old version is faster in: ";
    EXPECT_GT(perf_data[0].reserve , perf_data[1].reserve ) << common_str << "reserve";
    EXPECT_GT(perf_data[0].set_type, perf_data[1].set_type) << common_str << "set_type";
    EXPECT_GT(perf_data[0].commit  , perf_data[1].commit  ) << common_str << "commit";
    EXPECT_GT(perf_data[0].uncommit, perf_data[1].uncommit) << common_str << "uncommit";
    EXPECT_GT(perf_data[0].release , perf_data[1].release ) << common_str << "release";
    tty->print_cr(" Old version, reserve: " SIZE_FORMAT_W(6) " set_type: " SIZE_FORMAT_W(6) " commit: " SIZE_FORMAT_W(6) " uncommit: " SIZE_FORMAT_W(6) " release: " SIZE_FORMAT_W(6),
                  perf_data[0].reserve, perf_data[0].set_type, perf_data[0].commit, perf_data[0].uncommit, perf_data[0].release);
    tty->print_cr(" New version, reserve: " SIZE_FORMAT_W(6) " set_type: " SIZE_FORMAT_W(6) " commit: " SIZE_FORMAT_W(6) " uncommit: " SIZE_FORMAT_W(6) " release: " SIZE_FORMAT_W(6),
                  perf_data[1].reserve, perf_data[1].set_type, perf_data[1].commit, perf_data[1].uncommit, perf_data[1].release);
  }
};

using VMTOld = VirtualMemoryTracker;
using VMTNew = VirtualMemoryTrackerWithTree::Instance;
using VMS = VirtualMemorySummary;
using SummaryDiff = VMATree::SummaryDiff;

bool same_diffs(SummaryDiff d1, SummaryDiff d2) {
  bool result = true;
  for (int i = 0; i < mt_number_of_types; i++) {
    if (d1.flag[i].reserve != d2.flag[i].reserve) {
      log_debug(nmt)("compare diffs: reserve %s, Old " SSIZE_FORMAT " != New " SSIZE_FORMAT,
                    NMTUtil::flag_to_name(NMTUtil::index_to_flag(i)),
                    (ssize_t)d1.flag[i].reserve,
                    (ssize_t)d2.flag[i].reserve
                    );
      result = false;
    }
    if (d1.flag[i].commit != d2.flag[i].commit) {
      log_debug(nmt)("compare diffs: commit %s, Old " SSIZE_FORMAT " != New " SSIZE_FORMAT,
                    NMTUtil::flag_to_name(NMTUtil::index_to_flag(i)),
                    (ssize_t)d1.flag[i].commit,
                    (ssize_t)d2.flag[i].commit
                    );
      result = false;
    }
  }
  return result;
}

SummaryDiff vms_diff(VirtualMemorySnapshot* vms) {
  SummaryDiff diff;
  for (int i = 0; i < mt_number_of_types; i++) {
    const MEMFLAGS f = NMTUtil::index_to_flag(i);
    size_t old_res = vms->by_type(f)->reserved();
    size_t old_com = vms->by_type(f)->committed();
    size_t new_res = VirtualMemorySummary::as_snapshot()->by_type(f)->reserved();
    size_t new_com = VirtualMemorySummary::as_snapshot()->by_type(f)->committed();
    VMATree::SingleDiff::delta res_diff = new_res - old_res;
    VMATree::SingleDiff::delta com_diff = new_com - old_com;
    diff.flag[i].reserve = res_diff;
    diff.flag[i].commit  = com_diff;
  }
  return diff;
}

#define COMMON_DEFS           \
  NativeCallStack ncs;        \
  VirtualMemorySnapshot vms;  \
  ThreadCritical tc;          \
  ResourceMark rm;            \
  if (!MemTracker::enabled()) return;

#define CALL_OLD_AND_NEW(func_to_call)  \
  VMTNew::func_to_call;                 \
  VMTOld::func_to_call;

#define CALL_AND_COMPARE(func_to_call)        \
  VMS::as_snapshot()->copy_to(&vms);          \
  VMTOld::func_to_call ;                      \
  SummaryDiff diff_old = vms_diff(&vms);      \
  VMS::as_snapshot()->copy_to(&vms);          \
  VMTNew::func_to_call ;                      \
  SummaryDiff diff_new = vms_diff(&vms);      \
  EXPECT_TRUE(same_diffs(diff_old, diff_new));



TEST_VM(VMTWithTree, AddReservedRegion) {
  COMMON_DEFS;
  CALL_AND_COMPARE(add_reserved_region(1200_a, 100, ncs, mtTest));
  CALL_OLD_AND_NEW(remove_released_region(1200_a, 100));
}

TEST_VM(VMTWithTree, AddCommittedRegion) {
  COMMON_DEFS;
  CALL_OLD_AND_NEW(add_reserved_region(2200_a, 100, ncs, mtTest));
  CALL_AND_COMPARE(add_committed_region(2250_a, 10, ncs));
  CALL_OLD_AND_NEW(remove_released_region(2200_a, 100));
}

TEST_VM(VMTWithTree, RemoveUncommittedRegion) {
  COMMON_DEFS;
  CALL_OLD_AND_NEW(add_reserved_region(2200_a, 100, ncs, mtTest));
  CALL_OLD_AND_NEW(add_committed_region(2250_a, 10, ncs));
  CALL_AND_COMPARE(remove_uncommitted_region(2255_a, 5));
  CALL_OLD_AND_NEW(remove_released_region(2200_a, 100));
}

TEST_VM(VMTWithTree, ReleaseRegionPartial) {
  COMMON_DEFS;
  CALL_OLD_AND_NEW(add_reserved_region(2200_a, 100, ncs, mtTest));
  CALL_OLD_AND_NEW(add_committed_region(2250_a, 10, ncs));
  CALL_AND_COMPARE(remove_released_region(2220_a, 50));
  CALL_OLD_AND_NEW(remove_released_region(2270_a, 30));
  CALL_OLD_AND_NEW(remove_released_region(2200_a, 20));
}

TEST_VM(VMTWithTree, ReleaseRegionWhole) {
  COMMON_DEFS;
  CALL_OLD_AND_NEW(add_reserved_region(2400_a, 100, ncs, mtTest));
  CALL_OLD_AND_NEW(add_committed_region(2450_a, 10, ncs));
  CALL_AND_COMPARE(remove_released_region(2400_a, 100));
}

TEST_VM(VMTWithTree, SetRegionType) {
  COMMON_DEFS;
  CALL_OLD_AND_NEW(add_reserved_region(2500_a, 100, ncs, mtNone));
  CALL_AND_COMPARE(set_reserved_region_type(2500_a, mtClass));
  CALL_OLD_AND_NEW(remove_released_region(2500_a, 100));
}

TEST_VM(VMTWithTree, SplitRegion) {
  COMMON_DEFS;
  CALL_OLD_AND_NEW(add_reserved_region(4200_a, 100, ncs, mtTest));
  CALL_AND_COMPARE(split_reserved_region(4200_a, 100, 30, mtClass, mtClassShared));
  CALL_OLD_AND_NEW(remove_released_region(4200_a, 30));
  CALL_OLD_AND_NEW(remove_released_region(4230_a, 70));
}

TEST_VM(VMTWithTree, PrintContainingRegion) {
  COMMON_DEFS;
  CALL_OLD_AND_NEW(add_reserved_region(7200_a, 100, ncs, mtTest));
  CALL_OLD_AND_NEW(add_reserved_region(7400_a, 100, ncs, mtTest));
  CALL_OLD_AND_NEW(add_reserved_region(7600_a, 100, ncs, mtTest));

  EXPECT_TRUE(VMTOld::print_containing_region(7450_a, tty));
  EXPECT_TRUE(VMTNew::print_containing_region(7450_a, tty));
  CALL_OLD_AND_NEW(remove_released_region(7200_a, 100));
  CALL_OLD_AND_NEW(remove_released_region(7400_a, 100));
  CALL_OLD_AND_NEW(remove_released_region(7600_a, 100));
}

TEST_VM(VMTWithTree, WalkVirtualMemory) {
  COMMON_DEFS;
  CALL_OLD_AND_NEW(add_reserved_region(8200_a, 100, ncs, mtTest));
  CALL_OLD_AND_NEW(add_reserved_region(8400_a, 100, ncs, mtTest));
  CALL_OLD_AND_NEW(add_reserved_region(8600_a, 100, ncs, mtTest));
  class WalkerTest : public VirtualMemoryWalker {
    bool do_allocation_site(const ReservedMemoryRegion* rgn) override {
      if (rgn->flag() != mtTest)
        return true;
      EXPECT_EQ(rgn->size(), 100UL);
      EXPECT_LT((size_t)rgn->base() / 1000, 10UL);
      EXPECT_EQ((size_t)rgn->base() % 10, 0UL);
      return true;
    }
  };
  WalkerTest walker;
  VMTOld::walk_virtual_memory(&walker);
  VMTNew::walk_virtual_memory(&walker);
}

TEST_VM_F(VMTWithVMATreeTest, PerformanceComparison) {

  tty->print_cr("\n\nPerformance comparison of two versions is skipped.\n\n");
  return;
  using Ver = MemTracker::VMT_Version;

  for (int i: {0, 1}) {
    MemTracker::set_version(i == 0 ? Ver::OLD : Ver::NEW);
    vmt_cycle();
  }
  compare_and_report_perf_data();
}

