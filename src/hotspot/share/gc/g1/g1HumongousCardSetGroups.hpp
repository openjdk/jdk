/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1HUMONGOUSCARDSETGROUPS_HPP
#define SHARE_GC_G1_G1HUMONGOUSCARDSETGROUPS_HPP

#include "gc/g1/g1CardSetGroup.hpp"

// Manages card set groups for humongous objects.
//
// This container is the owner of all humongous object card set groups currently active.
// Card set groups may be of any state, i.e. both Updating and Complete. Their regions are not
// automatically eligible for eager reclaim if they are in this set.
//
// Card set groups for humongous objects are per humongous object. Such a card set group
// contains all references to regions covered by the humongous object. The first region in this
// group must be the humongous-starts region, followed by any humongous-continues regions in
// address order.
//
// Some humongous objects may not have a card set group, for example after Full GC or when
// tracking is dropped.
//
class G1HumongousCardSetGroups {
  G1CardSetGroupList _groups;

  G1CardSetGroup* new_group(G1HeapRegion* starts_humongous, G1CardSetGroup::State state);

public:
  ~G1HumongousCardSetGroups();

  void clear();

  void add_newly_allocated(G1HeapRegion* starts_humongous);
  void add_for_rebuild(GrowableArrayCHeap<G1HeapRegion*, mtGC>* regions);

  void prepare_for_scan();
  void after_rebuild();

  void verify();

  template <typename FUNC>
  void iterate(FUNC&& fn) const;

  // Apply fn to every humongous card set group tracked. If the predicate returns true,
  // removes, uninstalls and deletes the card set groups after iteration.
  // The predicate must not change the region list or delete the group.
  template <typename EVAL>
  void clean(EVAL&& fn);
};

#endif // SHARE_GC_G1_G1HUMONGOUSCARDSETGROUPS_HPP
