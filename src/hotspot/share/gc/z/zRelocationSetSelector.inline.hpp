/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZRELOCATIONSETSELECTOR_INLINE_HPP
#define SHARE_GC_Z_ZRELOCATIONSETSELECTOR_INLINE_HPP

#include "gc/z/zRelocationSetSelector.hpp"

#include "gc/z/zArray.inline.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageAge.inline.hpp"
#include "utilities/powerOfTwo.hpp"

inline size_t ZRelocationSetSelectorGroupStats::npages_candidates() const {
  return _npages_candidates;
}

inline size_t ZRelocationSetSelectorGroupStats::total() const {
  return _total;
}

inline size_t ZRelocationSetSelectorGroupStats::live() const {
  return _live;
}

inline size_t ZRelocationSetSelectorGroupStats::empty() const {
  return _empty;
}

inline size_t ZRelocationSetSelectorGroupStats::npages_selected() const {
  return _npages_selected;
}

inline size_t ZRelocationSetSelectorGroupStats::relocate() const {
  return _relocate;
}

inline bool ZRelocationSetSelectorStats::has_relocatable_pages() const {
  return _has_relocatable_pages;
}

inline const ZRelocationSetSelectorGroupStats& ZRelocationSetSelectorStats::small(ZPageAge age) const {
  return _small[untype(age)];
}

inline const ZRelocationSetSelectorGroupStats& ZRelocationSetSelectorStats::medium(ZPageAge age) const {
  return _medium[untype(age)];
}

inline const ZRelocationSetSelectorGroupStats& ZRelocationSetSelectorStats::large(ZPageAge age) const {
  return _large[untype(age)];
}

inline bool ZRelocationSetSelectorGroup::pre_filter_page(const ZPage* page, size_t live_bytes) const {
  if (page->is_small()) {
    // Small pages are always the same size, so we can simply compare the
    // garbage pre-calculated _page_fragmentation_limit.
    assert(page->size() == ZPageSizeSmall, "Unexpected small page size %zu", page->size());

    const size_t garbage = ZPageSizeSmall - live_bytes;

    return garbage > _page_fragmentation_limit;
  }

  if (page->is_medium()) {
    // Medium pages may have different sizes, so we can recalculate the page
    // fragmentation limit for a specific page by multiplying pre-calculated
    // _page_fragmentation_limit (calculated using the max page size) by the
    // fraction the specific page size is of the max page size.
    // Because the page sizes are always a power of two we can rewrite this
    // using log2 and bit-shift.
    const size_t size = page->size();
    const int shift = ZPageSizeMediumMaxShift - log2i_exact(size);
    const size_t page_fragmentation_limit = _page_fragmentation_limit >> shift;

    const size_t garbage = size - live_bytes;

    return garbage > page_fragmentation_limit;
  }

  // Large pages are never relocated
  return false;
}

inline void ZRelocationSetSelectorGroup::register_live_page(ZPage* page) {
  const size_t live = page->live_bytes();

  // Pre-filter out pages that are guaranteed to not be selected
  if (pre_filter_page(page, live)) {
    _live_pages.append(page);
  } else if (page->is_young()) {
    _not_selected_pages.append(page);
  }

  const size_t size = page->size();
  const uint age = untype(page->age());
  _stats[age]._npages_candidates++;
  _stats[age]._total += size;
  _stats[age]._live += live;
}

inline void ZRelocationSetSelectorGroup::register_empty_page(ZPage* page) {
  const size_t size = page->size();

  const uint age = untype(page->age());
  _stats[age]._npages_candidates++;
  _stats[age]._total += size;
  _stats[age]._empty += size;
}

inline const ZArray<ZPage*>* ZRelocationSetSelectorGroup::selected_pages() const {
  return &_live_pages;
}

inline const ZArray<ZPage*>* ZRelocationSetSelectorGroup::not_selected_pages() const {
  return &_not_selected_pages;
}

inline size_t ZRelocationSetSelectorGroup::forwarding_entries() const {
  return _forwarding_entries;
}

inline const ZRelocationSetSelectorGroupStats& ZRelocationSetSelectorGroup::stats(ZPageAge age) const {
  return _stats[untype(age)];
}

inline void ZRelocationSetSelector::register_live_page(ZPage* page) {
  page->log_msg(" (relocation candidate)");

  const ZPageType type = page->type();

  if (type == ZPageType::small) {
    _small.register_live_page(page);
  } else if (type == ZPageType::medium) {
    _medium.register_live_page(page);
  } else {
    _large.register_live_page(page);
  }
}

inline void ZRelocationSetSelector::register_empty_page(ZPage* page) {
  page->log_msg(" (relocation empty)");

  const ZPageType type = page->type();

  if (type == ZPageType::small) {
    _small.register_empty_page(page);
  } else if (type == ZPageType::medium) {
    _medium.register_empty_page(page);
  } else {
    _large.register_empty_page(page);
  }

  _empty_pages.append(page);
}

inline bool ZRelocationSetSelector::should_free_empty_pages(int bulk) const {
  return _empty_pages.length() >= bulk && _empty_pages.is_nonempty();
}

inline const ZArray<ZPage*>* ZRelocationSetSelector::empty_pages() const {
  return &_empty_pages;
}

inline void ZRelocationSetSelector::clear_empty_pages() {
  return _empty_pages.clear();
}

inline size_t ZRelocationSetSelector::total() const {
  size_t sum = 0;
  for (ZPageAge age : ZPageAgeRange()) {
    sum += _small.stats(age).total() + _medium.stats(age).total() + _large.stats(age).total();
  }
  return sum;
}

inline size_t ZRelocationSetSelector::empty() const {
  size_t sum = 0;
  for (ZPageAge age : ZPageAgeRange()) {
    sum += _small.stats(age).empty() + _medium.stats(age).empty() + _large.stats(age).empty();
  }
  return sum;
}

inline size_t ZRelocationSetSelector::relocate() const {
  size_t sum = 0;
  for (ZPageAge age : ZPageAgeRange()) {
    sum += _small.stats(age).relocate() + _medium.stats(age).relocate() + _large.stats(age).relocate();
  }
  return sum;
}

inline const ZArray<ZPage*>* ZRelocationSetSelector::selected_small() const {
  return _small.selected_pages();
}

inline const ZArray<ZPage*>* ZRelocationSetSelector::selected_medium() const {
  return _medium.selected_pages();
}

inline const ZArray<ZPage*>* ZRelocationSetSelector::not_selected_small() const {
  return _small.not_selected_pages();
}

inline const ZArray<ZPage*>* ZRelocationSetSelector::not_selected_medium() const {
  return _medium.not_selected_pages();
}

inline const ZArray<ZPage*>* ZRelocationSetSelector::not_selected_large() const {
  return _large.not_selected_pages();
}

inline size_t ZRelocationSetSelector::forwarding_entries() const {
  return _small.forwarding_entries() + _medium.forwarding_entries();
}

#endif // SHARE_GC_Z_ZRELOCATIONSETSELECTOR_INLINE_HPP
