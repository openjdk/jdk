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

#ifndef SHARE_GC_G1_G1COLLECTIONSET_INLINE_HPP
#define SHARE_GC_G1_G1COLLECTIONSET_INLINE_HPP

#include "gc/g1/g1CollectionSet.hpp"

#include "gc/g1/g1HeapRegionRemSet.hpp"

template <class CardOrRangeVisitor>
inline void G1CollectionSet::merge_cardsets_for_collection_groups(CardOrRangeVisitor& cl, uint worker_id, uint num_workers) {
  uint length = collection_groups_increment_length();
  uint offset =  _selected_groups_inc_part_start;
  if (length == 0) {
    return;
  }

  uint start_pos = (worker_id * length) / num_workers;
  uint cur_pos = start_pos;
  uint count = 0;
  do {
    G1HeapRegionRemSet::iterate_for_merge(collection_set_groups()->at(offset + cur_pos)->card_set(), cl);
    cur_pos++;
    count++;
    if (cur_pos == length) {
      cur_pos = 0;
    }
  } while (cur_pos != start_pos);
}
#endif /* SHARE_GC_G1_G1COLLECTIONSET_INLINE_HPP */
