/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1BlockOffsetTable.inline.hpp"
#include "gc/g1/g1CardSet.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionSet.hpp"
#include "gc/g1/g1RegionToSpaceMapper.hpp"
#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "memory/virtualspace.hpp"
#include "unittest.hpp"

// @requires UseG1GC
TEST_OTHER_VM(G1FreeRegionList, length) {
  if (!UseG1GC) {
    return;
  }

  G1FreeRegionList l("test");
  const uint num_regions_in_test = 5;

  // Create a fake heap. It does not need to be valid, as the G1HeapRegion constructor
  // does not access it.
  MemRegion heap(nullptr, num_regions_in_test * G1HeapRegion::GrainWords);

  // Allocate a fake BOT because the G1HeapRegion constructor initializes
  // the BOT.
  size_t bot_size = G1BlockOffsetTable::compute_size(heap.word_size());
  HeapWord* bot_data = NEW_C_HEAP_ARRAY(HeapWord, bot_size, mtGC);
  ReservedSpace bot_rs(G1BlockOffsetTable::compute_size(heap.word_size()));
  G1RegionToSpaceMapper* bot_storage =
    G1RegionToSpaceMapper::create_mapper(bot_rs,
                                         bot_rs.size(),
                                         os::vm_page_size(),
                                         G1HeapRegion::GrainBytes,
                                         CardTable::card_size(),
                                         mtGC);
  G1BlockOffsetTable bot(heap, bot_storage);
  bot_storage->commit_regions(0, num_regions_in_test);

  // Set up memory regions for the heap regions.
  MemRegion mr0(heap.start(), G1HeapRegion::GrainWords);
  MemRegion mr1(mr0.end(), G1HeapRegion::GrainWords);
  MemRegion mr2(mr1.end(), G1HeapRegion::GrainWords);
  MemRegion mr3(mr2.end(), G1HeapRegion::GrainWords);
  MemRegion mr4(mr3.end(), G1HeapRegion::GrainWords);

  G1CardSetConfiguration config;

  G1HeapRegion hr0(0, &bot, mr0, &config);
  G1HeapRegion hr1(1, &bot, mr1, &config);
  G1HeapRegion hr2(2, &bot, mr2, &config);
  G1HeapRegion hr3(3, &bot, mr3, &config);
  G1HeapRegion hr4(4, &bot, mr4, &config);

  l.add_ordered(&hr1);
  l.add_ordered(&hr0);
  l.add_ordered(&hr3);
  l.add_ordered(&hr4);
  l.add_ordered(&hr2);

  EXPECT_EQ(l.length(), num_regions_in_test) << "Wrong free region list length";
  l.verify_list();

  bot_storage->uncommit_regions(0, num_regions_in_test);
  delete bot_storage;
  FREE_C_HEAP_ARRAY(HeapWord, bot_data);
}
