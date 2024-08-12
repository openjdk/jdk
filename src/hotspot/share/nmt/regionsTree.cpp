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
#include "nmt/regionsTree.hpp"

ReservedMemoryRegion RegionsTree::find_reserved_region(address addr, bool with_trace) {
    ReservedMemoryRegion rmr;
    bool dump = with_trace;
    auto contain_region = [&](ReservedMemoryRegion& region_in_tree) {
      if (dump) {
        tty->print_cr("trc base: " INTPTR_FORMAT " , trc end: " INTPTR_FORMAT,
                      p2i(region_in_tree.base()), p2i(region_in_tree.end()));
      }
      if (region_in_tree.contain_address(addr)) {
        rmr = region_in_tree;
        return false;
      }
      return true;
    };
    visit_reserved_regions(contain_region);
    return rmr;
}

VMATree::SummaryDiff RegionsTree::commit_region(address addr, size_t size, const NativeCallStack& stack) {
  ReservedMemoryRegion rgn = find_reserved_region(addr);
  if (rgn.base() == (address)1) {
    tty->print_cr("commit region not-found " INTPTR_FORMAT " end: " INTPTR_FORMAT, p2i(addr), p2i(addr + size));
    dump(tty);
    rgn = find_reserved_region(addr, true);
    ShouldNotReachHere();
  }
  return commit_mapping((VMATree::position)addr, size, make_region_data(stack, rgn.flag()));

}

VMATree::SummaryDiff RegionsTree::uncommit_region(address addr, size_t size) {
  ReservedMemoryRegion rgn = find_reserved_region(addr);
  if (rgn.base() == (address)1) {
    tty->print_cr("uncommit region not-found " INTPTR_FORMAT " end: " INTPTR_FORMAT, p2i(addr), p2i(addr + size));
    dump(tty);
    rgn = find_reserved_region(addr, true);
    ShouldNotReachHere();
  }
  return reserve_mapping((VMATree::position)addr, size, make_region_data(NativeCallStack::empty_stack(), rgn.flag()));
}


