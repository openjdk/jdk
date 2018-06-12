/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zRelocationSetSelector.hpp"
#include "logging/log.hpp"
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"

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
    _relocating(0),
    _fragmentation(0) {}

ZRelocationSetSelectorGroup::~ZRelocationSetSelectorGroup() {
  FREE_C_HEAP_ARRAY(const ZPage*, _sorted_pages);
}

void ZRelocationSetSelectorGroup::register_live_page(const ZPage* page, size_t garbage) {
  if (garbage > _fragmentation_limit) {
    _registered_pages.add(page);
  } else {
    _fragmentation += garbage;
  }
}

void ZRelocationSetSelectorGroup::semi_sort() {
  // Semi-sort registered pages by live bytes in ascending order
  const size_t npartitions_shift = 11;
  const size_t npartitions = (size_t)1 << npartitions_shift;
  const size_t partition_size = _page_size >> npartitions_shift;
  const size_t partition_size_shift = exact_log2(partition_size);
  const size_t npages = _registered_pages.size();

  size_t partition_slots[npartitions];
  size_t partition_finger[npartitions];

  // Allocate destination array
  _sorted_pages = REALLOC_C_HEAP_ARRAY(const ZPage*, _sorted_pages, npages, mtGC);
  debug_only(memset(_sorted_pages, 0, npages * sizeof(ZPage*)));

  // Calculate partition slots
  memset(partition_slots, 0, sizeof(partition_slots));
  ZArrayIterator<const ZPage*> iter1(&_registered_pages);
  for (const ZPage* page; iter1.next(&page);) {
    const size_t index = page->live_bytes() >> partition_size_shift;
    partition_slots[index]++;
  }

  // Calculate accumulated partition slots and fingers
  size_t prev_partition_slots = 0;
  for (size_t i = 0; i < npartitions; i++) {
    partition_slots[i] += prev_partition_slots;
    partition_finger[i] = prev_partition_slots;
    prev_partition_slots = partition_slots[i];
  }

  // Sort pages into partitions
  ZArrayIterator<const ZPage*> iter2(&_registered_pages);
  for (const ZPage* page; iter2.next(&page);) {
    const size_t index = page->live_bytes() >> partition_size_shift;
    const size_t finger = partition_finger[index]++;
    assert(_sorted_pages[finger] == NULL, "Invalid finger");
    _sorted_pages[finger] = page;
  }
}

void ZRelocationSetSelectorGroup::select() {
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
  _relocating = from_size;
  for (size_t i = _nselected; i < npages; i++) {
    const ZPage* const page = _sorted_pages[i];
    _fragmentation += page->size() - page->live_bytes();
  }

  log_debug(gc, reloc)("Relocation Set (%s Pages): " SIZE_FORMAT "->" SIZE_FORMAT ", " SIZE_FORMAT " skipped",
                       _name, selected_from, selected_to, npages - _nselected);
}

const ZPage* const* ZRelocationSetSelectorGroup::selected() const {
  return _sorted_pages;
}

size_t ZRelocationSetSelectorGroup::nselected() const {
  return _nselected;
}

size_t ZRelocationSetSelectorGroup::relocating() const {
  return _relocating;
}

size_t ZRelocationSetSelectorGroup::fragmentation() const {
  return _fragmentation;
}

ZRelocationSetSelector::ZRelocationSetSelector() :
    _small("Small", ZPageSizeSmall, ZObjectSizeLimitSmall),
    _medium("Medium", ZPageSizeMedium, ZObjectSizeLimitMedium),
    _live(0),
    _garbage(0),
    _fragmentation(0) {}

void ZRelocationSetSelector::register_live_page(const ZPage* page) {
  const uint8_t type = page->type();
  const size_t live = page->live_bytes();
  const size_t garbage = page->size() - live;

  if (type == ZPageTypeSmall) {
    _small.register_live_page(page, garbage);
  } else if (type == ZPageTypeMedium) {
    _medium.register_live_page(page, garbage);
  } else {
    _fragmentation += garbage;
  }

  _live += live;
  _garbage += garbage;
}

void ZRelocationSetSelector::register_garbage_page(const ZPage* page) {
  _garbage += page->size();
}

void ZRelocationSetSelector::select(ZRelocationSet* relocation_set) {
  // Select pages to relocate. The resulting relocation set will be
  // sorted such that medium pages comes first, followed by small
  // pages. Pages within each page group will be semi-sorted by live
  // bytes in ascending order. Relocating pages in this order allows
  // us to start reclaiming memory more quickly.

  // Select pages from each group
  _medium.select();
  _small.select();

  // Populate relocation set
  relocation_set->populate(_medium.selected(), _medium.nselected(),
                           _small.selected(), _small.nselected());
}

size_t ZRelocationSetSelector::live() const {
  return _live;
}

size_t ZRelocationSetSelector::garbage() const {
  return _garbage;
}

size_t ZRelocationSetSelector::relocating() const {
  return _small.relocating() + _medium.relocating();
}

size_t ZRelocationSetSelector::fragmentation() const {
  return _fragmentation + _small.fragmentation() + _medium.fragmentation();
}
