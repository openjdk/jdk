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
#ifndef SHARE_NMT_REGIONSTREE_INLINE_HPP
#define SHARE_NMT_REGIONSTREE_INLINE_HPP

#include "nmt/regionsTree.hpp"
#include "nmt/virtualMemoryTracker.hpp"

template<typename F>
void RegionsTree::visit_committed_regions(const ReservedMemoryRegion& rgn, F func) {
  position start = (position)rgn.base();
  size_t end = reinterpret_cast<size_t>(rgn.end()) + 1;
  size_t comm_size = 0;

  NodeHelper prev;
  visit_range_in_order(start, end, [&](Node* node) {
    NodeHelper curr(node);
    if (prev.is_valid() && prev.is_committed_begin()) {
      CommittedMemoryRegion cmr((address)prev.position(), curr.distance_from(prev), stack(prev));
      if (!func(cmr)) {
        return false;
      }
    }
    prev = curr;
    return true;
  });
}

template<typename F>
void RegionsTree::visit_reserved_regions(F func) {
  NodeHelper begin_node, prev;
  size_t rgn_size = 0;

  visit_in_order([&](Node* node) {
    NodeHelper curr(node);
    if (prev.is_valid()) {
      rgn_size += curr.distance_from(prev);
    } else {
      begin_node = curr;
      rgn_size = 0;
    }
    prev = curr;
    if (curr.is_released_begin() || begin_node.out_tag() != curr.out_tag()) {
      auto st = stack(begin_node);
      if (rgn_size == 0) {
        prev.clear_node();
        return true;
      }
      ReservedMemoryRegion rmr((address)begin_node.position(), rgn_size, st, begin_node.out_tag());
      if (!func(rmr)) {
        return false;
      }
      rgn_size = 0;
      if (!curr.is_released_begin()) {
        begin_node = curr;
      } else {
        begin_node.clear_node();
        prev.clear_node();
      }
    }

    return true;
  });
}

#endif //SHARE_NMT_REGIONSTREE_INLINE_HPP
