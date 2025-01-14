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

VMATree::SummaryDiff RegionsTree::commit_region(address addr, size_t size, const NativeCallStack& stack) {
  return commit_mapping((VMATree::position)addr, size, make_region_data(stack, mtNone), /*use tag inplace*/ true);
}

VMATree::SummaryDiff RegionsTree::uncommit_region(address addr, size_t size) {
  return uncommit_mapping((VMATree::position)addr, size, make_region_data(NativeCallStack::empty_stack(), mtNone));
}
// The nodes for the regions may look like this:
// small letters are existing nodes, capital A and B are the region we are going to find the summary.
// ...--------a-----A----b---c---d----e---B---f---....
// calling visit_range_in_order for [A,B) is not enough to find regions between a---...---f
VMATree::SummaryDiff RegionsTree::region_summary(address addr, size_t size) {
  NodeHelper prev;
  SummaryDiff summary;
  VMATree::position A = (VMATree::position)addr;
  VMATree::position B = (VMATree::position)A + size;
  VMATree::VMATreap::Range ab = tree().find_enclosing_range(A);
  VMATree::VMATreap::Range ef = tree().find_enclosing_range(B);
  VMATree::position a = ab.start == nullptr ? A : ab.start->key();
  VMATree::position f = ef.end == nullptr ? B : ef.end->key();


  visit_range_in_order(a, f, [&](Node* node) {
    NodeHelper curr(node);
    if (prev.is_valid()) {
      SingleDiff& single = summary.tag[NMTUtil::tag_to_index(prev.out_tag())];
      size_t dist = curr.distance_from(prev);
      if (prev.is_reserved_begin())
        single.reserve += dist;
      if (prev.is_committed_begin()) {
        single.reserve += dist;
        single.commit += dist;
      }
    }
    prev = curr;
    return true;
  });
  return summary;
}


