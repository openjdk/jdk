/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1FROMCARDCACHE_INLINE_HPP
#define SHARE_GC_G1_G1FROMCARDCACHE_INLINE_HPP

#include "gc/g1/g1FromCardCache.hpp"

bool G1FromCardCache::contains_or_add(uintptr_t from_card, uint card_set_group_id) {
  if (_from_card != from_card) {
    _from_card = from_card;
    _num_card_set_groups = 0;
  }

  for (uint i = 0; i < _num_card_set_groups; i++) {
    if (_card_set_group_ids[i] == card_set_group_id) {
      return true;
    }
  }

  assert(_num_card_set_groups < MaxGroupsPerCard, "from_card has too many destination card set groups");

  _card_set_group_ids[_num_card_set_groups++] = card_set_group_id;
  return false;
}

#endif // SHARE_GC_G1_G1FROMCARDCACHE_INLINE_HPP
