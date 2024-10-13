/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_X_XRELOCATIONSETSELECTOR_INLINE_HPP
#define SHARE_GC_X_XRELOCATIONSETSELECTOR_INLINE_HPP

#include "gc/x/xRelocationSetSelector.hpp"

#include "gc/x/xArray.inline.hpp"
#include "gc/x/xPage.inline.hpp"

inline size_t XRelocationSetSelectorGroupStats::npages_candidates() const {
  return _npages_candidates;
}

inline size_t XRelocationSetSelectorGroupStats::total() const {
  return _total;
}

inline size_t XRelocationSetSelectorGroupStats::live() const {
  return _live;
}

inline size_t XRelocationSetSelectorGroupStats::empty() const {
  return _empty;
}

inline size_t XRelocationSetSelectorGroupStats::npages_selected() const {
  return _npages_selected;
}

inline size_t XRelocationSetSelectorGroupStats::relocate() const {
  return _relocate;
}

inline const XRelocationSetSelectorGroupStats& XRelocationSetSelectorStats::small() const {
  return _small;
}

inline const XRelocationSetSelectorGroupStats& XRelocationSetSelectorStats::medium() const {
  return _medium;
}

inline const XRelocationSetSelectorGroupStats& XRelocationSetSelectorStats::large() const {
  return _large;
}

inline void XRelocationSetSelectorGroup::register_live_page(XPage* page) {
  const uint8_t type = page->type();
  const size_t size = page->size();
  const size_t live = page->live_bytes();
  const size_t garbage = size - live;

  if (garbage > _fragmentation_limit) {
    _live_pages.append(page);
  }

  _stats._npages_candidates++;
  _stats._total += size;
  _stats._live += live;
}

inline void XRelocationSetSelectorGroup::register_empty_page(XPage* page) {
  const size_t size = page->size();

  _stats._npages_candidates++;
  _stats._total += size;
  _stats._empty += size;
}

inline const XArray<XPage*>* XRelocationSetSelectorGroup::selected() const {
  return &_live_pages;
}

inline size_t XRelocationSetSelectorGroup::forwarding_entries() const {
  return _forwarding_entries;
}

inline const XRelocationSetSelectorGroupStats& XRelocationSetSelectorGroup::stats() const {
  return _stats;
}

inline void XRelocationSetSelector::register_live_page(XPage* page) {
  const uint8_t type = page->type();

  if (type == XPageTypeSmall) {
    _small.register_live_page(page);
  } else if (type == XPageTypeMedium) {
    _medium.register_live_page(page);
  } else {
    _large.register_live_page(page);
  }
}

inline void XRelocationSetSelector::register_empty_page(XPage* page) {
  const uint8_t type = page->type();

  if (type == XPageTypeSmall) {
    _small.register_empty_page(page);
  } else if (type == XPageTypeMedium) {
    _medium.register_empty_page(page);
  } else {
    _large.register_empty_page(page);
  }

  _empty_pages.append(page);
}

inline bool XRelocationSetSelector::should_free_empty_pages(int bulk) const {
  return _empty_pages.length() >= bulk && _empty_pages.is_nonempty();
}

inline const XArray<XPage*>* XRelocationSetSelector::empty_pages() const {
  return &_empty_pages;
}

inline void XRelocationSetSelector::clear_empty_pages() {
  return _empty_pages.clear();
}

inline size_t XRelocationSetSelector::total() const {
  return _small.stats().total() + _medium.stats().total() + _large.stats().total();
}

inline size_t XRelocationSetSelector::empty() const {
  return _small.stats().empty() + _medium.stats().empty() + _large.stats().empty();
}

inline size_t XRelocationSetSelector::relocate() const {
  return _small.stats().relocate() + _medium.stats().relocate() + _large.stats().relocate();
}

inline const XArray<XPage*>* XRelocationSetSelector::small() const {
  return _small.selected();
}

inline const XArray<XPage*>* XRelocationSetSelector::medium() const {
  return _medium.selected();
}

inline size_t XRelocationSetSelector::forwarding_entries() const {
  return _small.forwarding_entries() + _medium.forwarding_entries();
}

#endif // SHARE_GC_X_XRELOCATIONSETSELECTOR_INLINE_HPP
