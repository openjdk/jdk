/*
 * Copyright (c) 2021, 2022 SAP SE. All rights reserved.
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "memory/allocation.hpp"
#include "nmt/vmatree.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/os.hpp"
#include "utilities/ostream.hpp"
#include "unittest.hpp"

template <bool new_impl, bool dolock>
struct Implementation {
  VMATree vtree;

  Implementation()
  : vtree(){}

  void register_reservation(address addr, size_t size, MEMFLAGS f) {
    if (new_impl) {
      if (dolock) {
        ThreadCritical tc;
        VMATree::Metadata md({}, f);
        vtree.reserve_mapping((size_t)addr,  size, md);
      } else {
        VMATree::Metadata md({}, f);
        vtree.reserve_mapping((size_t)addr,  size, md);
      }
    } else {
      if (dolock) {
        ThreadCritical tc;
        VirtualMemoryTracker::add_reserved_region(addr, size, CALLER_PC, f);
      } else {
        VirtualMemoryTracker::add_reserved_region(addr, size, CALLER_PC, f);
      }
    }
  }

  void register_commit(address addr, size_t size, MEMFLAGS f) {
    if (new_impl) {
      if (dolock) {
        ThreadCritical tc;
        VMATree::Metadata md({}, f);
        vtree.commit_mapping((size_t)addr,  size, md);
      } else {
        VMATree::Metadata md({}, f);
        vtree.commit_mapping((size_t)addr,  size, md);
      }
    } else {
      if (dolock) {
        ThreadCritical tc;
        VirtualMemoryTracker::add_committed_region(addr, size, CALLER_PC);
      } else {
        VirtualMemoryTracker::add_committed_region(addr, size, CALLER_PC);
      }
    }
  }

  void register_uncommit(address addr, size_t size, MEMFLAGS f) {
    if (new_impl) {
      if (dolock) {
        ThreadCritical tc;
        VMATree::Metadata md({}, f);
        vtree.reserve_mapping((size_t)addr,  size, md);
      } else {
        VMATree::Metadata md({}, f);
        vtree.reserve_mapping((size_t)addr,  size, md);
      }
    } else {
      if (dolock) {
        ThreadCritical tc;
        VirtualMemoryTracker::remove_uncommitted_region(addr, size);
      } else {
        VirtualMemoryTracker::remove_uncommitted_region(addr, size);
      }
    }
  }

  void print_summary() {
    if (new_impl) {
      if (dolock) {
        ThreadCritical tc;
      } else {
      }
    } else {
      if (dolock) {
        ThreadCritical tc;
        //MemTracker::final_report(tty);
      } else {
        //MemTracker::final_report(tty);
      }
    }
  }
};

template <bool new_impl, bool with_locking>
static void do_test_speed_1() {

  typedef Implementation<new_impl, with_locking> Impl;
  Impl impl;

  // prepare:
  // We create X reserved regions with Y committed regions in them.
  constexpr int num_reserved = 100;
  constexpr int num_committed = 10000;
  constexpr int num_regions = num_reserved * num_committed;

  constexpr size_t region_size = 4 * K;
  constexpr size_t step_size = region_size * 2;

  constexpr size_t reserved_size = num_committed * step_size;

  constexpr uintptr_t base_i = 0xFFFF000000000000ULL;
  const address base = (address)base_i;

  double d1 = os::elapsedTime();

  // Now, establish regions
  for (int i = 0; i < num_reserved; i++) {
    const address addr = base + (i * reserved_size);
    const MEMFLAGS f = ((i % 2) == 0) ? mtTest : mtNMT;
    impl.register_reservation(addr, reserved_size, f);

    // Establish committed regions
    for (int i2 = 0; i2 < num_committed; i2++) {
      const address addr2 = addr + (i2 * step_size);
      impl.register_commit(addr2, region_size, f);
    }
  }

  double d2 = os::elapsedTime();
  tty->print_cr("Setup: %f seconds", d2 - d1);

  // Now: randomly commit and uncommit regions.
  int num_operations = 1000000;
  int r = os::random();
  while (num_operations-- > 0) {
    r = os::next_random(r);
    const int res_i = r % num_reserved;
    r = os::next_random(r);
    const int com_i = r % num_committed;
    const MEMFLAGS f = ((res_i % 2) == 0) ? mtTest : mtNMT;
    const address addr = base + (res_i * reserved_size) + (com_i * step_size);
    impl.register_uncommit(addr, region_size, f);
    impl.register_commit(addr, region_size, f);
  }

  double d3 = os::elapsedTime();
  tty->print_cr("Test: %f seconds", d3 - d2);

  {
    double d = os::elapsedTime();
    impl.print_summary();
    double d2 = os::elapsedTime();
    tty->print_cr("Summary took %f seconds.", d2 - d);
  }
}

TEST_VM(NMTVMADict, test_speed_new_locked_1) {do_test_speed_1<true, true>();}
TEST_VM(NMTVMADict, test_speed_new_nolock_1)  {  do_test_speed_1<true, false>(); }
TEST_OTHER_VM(NMTVMADict, test_speed_old_locked_1)  {  do_test_speed_1<false, true>(); }
TEST_OTHER_VM(NMTVMADict, test_speed_old_nolock_1)  {  do_test_speed_1<false, false>(); }

