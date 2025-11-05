/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "nmt/regionsTree.hpp"
#include "nmt/regionsTree.inline.hpp"
#include "nmt/virtualMemoryTracker.hpp"

void RegionsTree::commit_region(address addr, size_t size, const NativeCallStack& stack, VMATree::SummaryDiff& diff) {
  commit_mapping((VMATree::position)addr, size, make_region_data(stack, mtNone), diff, /*use tag inplace*/ true);
}

void RegionsTree::uncommit_region(address addr, size_t size, VMATree::SummaryDiff& diff) {
  uncommit_mapping((VMATree::position)addr, size, make_region_data(NativeCallStack::empty_stack(), mtNone), diff);
}

#ifdef ASSERT
void RegionsTree::NodeHelper::print_on(outputStream* st) {
  auto st_str = [&](VMATree::StateType s){
    return s == VMATree::StateType::Released ? "Rl" :
           s == VMATree::StateType::Reserved ? "Rv" : "Cm";
  };
  st->print_cr("pos: " INTPTR_FORMAT " "
                "%s, %s <|> %s, %s",
                p2i((address)position()),
                st_str(in_state()),
                NMTUtil::tag_to_name(in_tag()),
                st_str(out_state()),
                NMTUtil::tag_to_name(out_tag())
                );
}

void RegionsTree::print_on(outputStream* st) {
  visit_in_order([&](const Node* node) {
    NodeHelper curr(const_cast<Node*>(node));
    curr.print_on(st);
    return true;
  });
}
#endif

size_t RegionsTree::committed_size(const ReservedMemoryRegion& rgn) {
  size_t result = 0;
  visit_committed_regions(rgn, [&](CommittedMemoryRegion& crgn) {
    result += crgn.size();
    return true;
  });
  return result;
}
