/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zArray.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zRelocationSet.hpp"
#include "gc/z/zRelocationSetSelector.inline.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"
#include "utilities/powerOfTwo.hpp"

ZRelocationSetSelectorGroupStats::ZRelocationSetSelectorGroupStats() :
    _npages(0),
    _total(0),
    _live(0),
    _garbage(0),
    _empty(0),
    _compacting_from(0),
    _compacting_to(0) {}

ZRelocationSetSelectorGroup::ZRelocationSetSelectorGroup(const char* name,
                                                         size_t page_size,
                                                         size_t object_size_limit) :
    _name(name),
    _page_size(page_size),
    _object_size_limit(object_size_limit),
    _fragmentation_limit(page_size * (ZFragmentationLimit / 100)),
    _registered_pages(),
    _sorted_pages(NULL),
    _nselected(0),
    _stats() {}

ZRelocationSetSelectorGroup::~ZRelocationSetSelectorGroup() {
  FREE_C_HEAP_ARRAY(ZPage*, _sorted_pages);
}

void ZRelocationSetSelectorGroup::register_live_page(ZPage* page) {
  const uint8_t type = page->type();
  const size_t size = page->size();
  const size_t live = page->live_bytes();
  const size_t garbage = size - live;

  if (garbage > _fragmentation_limit) {
    _registered_pages.add(page);
  }

  _stats._npages++;
  _stats._total += size;
  _stats._live += live;
  _stats._garbage += garbage;
}

void ZRelocationSetSelectorGroup::register_garbage_page(ZPage* page) {
  const size_t size = page->size();

  _stats._npages++;
  _stats._total += size;
  _stats._garbage += size;
  _stats._empty += size;
}

void ZRelocationSetSelectorGroup::semi_sort() {
  // Semi-sort registered pages by live bytes in ascending order
  const size_t npartitions_shift = 11;
  const size_t npartitions = (size_t)1 << npartitions_shift;
  const size_t partition_size = _page_size >> npartitions_shift;
  const size_t partition_size_shift = exact_log2(partition_size);
  const size_t npages = _registered_pages.size();

  // Partition slots/fingers
  size_t partitions[npartitions];

  // Allocate destination array
  assert(_sorted_pages == NULL, "Already initialized");
  _sorted_pages = NEW_C_HEAP_ARRAY(ZPage*, npages, mtGC);
  debug_only(memset(_sorted_pages, 0, npages * sizeof(ZPage*)));

  // Calculate partition slots
  memset(partitions, 0, sizeof(partitions));
  ZArrayIterator<ZPage*> iter1(&_registered_pages);
  for (ZPage* page; iter1.next(&page);) {
    const size_t index = page->live_bytes() >> partition_size_shift;
    partitions[index]++;
  }

  // Calculate partition fingers
  size_t finger = 0;
  for (size_t i = 0; i < npartitions; i++) {
    const size_t slots = partitions[i];
    partitions[i] = finger;
    finger += slots;
  }

  // Sort pages into partitions
  ZArrayIterator<ZPage*> iter2(&_registered_pages);
  for (ZPage* page; iter2.next(&page);) {
    const size_t index = page->live_bytes() >> partition_size_shift;
    const size_t finger = partitions[index]++;
    assert(_sorted_pages[finger] == NULL, "Invalid finger");
    _sorted_pages[finger] = page;
  }
}

void ZRelocationSetSelectorGroup::select() {
  if (_page_size == 0) {
    // Page type disabled
    return;
  }

  // Calculate the number of pages to relocate by successively including pages in
  // a candidate relocation set and calculate the maximum space requirement for
  // their live objects.
  const size_t npages = _registered_pages.size();
  size_t selected_from = 0;
  size_t selected_to = 0;
  size_t from_size = 0;

  semi_sort();

  for (size_t from = 1; from <= npages; from++) {
    // Add page to the candidate relocation set
    from_size += _sorted_pages[from - 1]->live_bytes();

    // Calculate the maximum number of pages needed by the candidate relocation set.
    // By subtracting the object size limit from the pages size we get the maximum
    // number of pages that the relocation set is guaranteed to fit in, regardless
    // of in which order the objects are relocated.
    const size_t to = ceil((double)(from_size) / (double)(_page_size - _object_size_limit));

    // Calculate the relative difference in reclaimable space compared to our
    // currently selected final relocation set. If this number is larger than the
    // acceptable fragmentation limit, then the current candidate relocation set
    // becomes our new final relocation set.
    const size_t diff_from = from - selected_from;
    const size_t diff_to = to - selected_to;
    const double diff_reclaimable = 100 - percent_of(diff_to, diff_from);
    if (diff_reclaimable > ZFragmentationLimit) {
      selected_from = from;
      selected_to = to;
    }

    log_trace(gc, reloc)("Candidate Relocation Set (%s Pages): "
                         SIZE_FORMAT "->" SIZE_FORMAT ", %.1f%% relative defragmentation, %s",
                         _name, from, to, diff_reclaimable, (selected_from == from) ? "Selected" : "Rejected");
  }

  // Finalize selection
  _nselected = selected_from;

  // Update statistics
  _stats._compacting_from = selected_from * _page_size;
  _stats._compacting_to = selected_to * _page_size;

  log_trace(gc, reloc)("Relocation Set (%s Pages): " SIZE_FORMAT "->" SIZE_FORMAT ", " SIZE_FORMAT " skipped",
                       _name, selected_from, selected_to, npages - _nselected);
}

ZRelocationSetSelector::ZRelocationSetSelector() :
    _small("Small", ZPageSizeSmall, ZObjectSizeLimitSmall),
    _medium("Medium", ZPageSizeMedium, ZObjectSizeLimitMedium),
    _large("Large", 0 /* page_size */, 0 /* object_size_limit */) {}

void ZRelocationSetSelector::register_live_page(ZPage* page) {
  const uint8_t type = page->type();

  if (type == ZPageTypeSmall) {
    _small.register_live_page(page);
  } else if (type == ZPageTypeMedium) {
    _medium.register_live_page(page);
  } else {
    _large.register_live_page(page);
  }
}

void ZRelocationSetSelector::register_garbage_page(ZPage* page) {
  const uint8_t type = page->type();

  if (type == ZPageTypeSmall) {
    _small.register_garbage_page(page);
  } else if (type == ZPageTypeMedium) {
    _medium.register_garbage_page(page);
  } else {
    _large.register_garbage_page(page);
  }
}

void ZRelocationSetSelector::select(ZRelocationSet* relocation_set) {
  // Select pages to relocate. The resulting relocation set will be
  // sorted such that medium pages comes first, followed by small
  // pages. Pages within each page group will be semi-sorted by live
  // bytes in ascending order. Relocating pages in this order allows
  // us to start reclaiming memory more quickly.

  // Select pages from each group, except large
  _medium.select();
  _small.select();

  // Populate relocation set
  relocation_set->populate(_medium.selected(), _medium.nselected(),
                           _small.selected(), _small.nselected());
}

ZRelocationSetSelectorStats ZRelocationSetSelector::stats() const {
  ZRelocationSetSelectorStats stats;
  stats._small = _small.stats();
  stats._medium = _medium.stats();
  stats._large = _large.stats();
  return stats;
}
