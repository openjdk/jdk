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

VMATree::SummaryDiff RegionsTree::commit_region(address addr, size_t size, const NativeCallStack& stack) {
  return commit_mapping((VMATree::position)addr, size, make_region_data(stack, mtNone), /*use tag inplace*/ true);
}

VMATree::SummaryDiff RegionsTree::uncommit_region(address addr, size_t size) {
  return uncommit_mapping((VMATree::position)addr, size, make_region_data(NativeCallStack::empty_stack(), mtNone));
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
  visit_in_order([&](Node* node) {
    NodeHelper curr(node);
    curr.print_on(st);
    return true;
  });
}
#endif