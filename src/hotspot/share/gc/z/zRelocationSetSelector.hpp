/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZRELOCATIONSETSELECTOR_HPP
#define SHARE_GC_Z_ZRELOCATIONSETSELECTOR_HPP

#include "gc/z/zArray.hpp"
#include "gc/z/zGenerationId.hpp"
#include "gc/z/zPageAge.hpp"
#include "gc/z/zPageType.hpp"
#include "memory/allocation.hpp"

class ZPage;

class ZRelocationSetSelectorGroupStats {
  friend class ZRelocationSetSelectorGroup;

private:
  // Candidate set
  size_t _npages_candidates;
  size_t _total;
  size_t _live;
  size_t _empty;

  // Selected set
  size_t _npages_selected;
  size_t _relocate;

public:
  ZRelocationSetSelectorGroupStats();

  size_t npages_candidates() const;
  size_t total() const;
  size_t live() const;
  size_t empty() const;

  size_t npages_selected() const;
  size_t relocate() const;
};

class ZRelocationSetSelectorStats {
  friend class ZRelocationSetSelector;

private:
  ZRelocationSetSelectorGroupStats _small[ZPageAgeCount];
  ZRelocationSetSelectorGroupStats _medium[ZPageAgeCount];
  ZRelocationSetSelectorGroupStats _large[ZPageAgeCount];

  size_t _has_relocatable_pages;

public:
  const ZRelocationSetSelectorGroupStats& small(ZPageAge age) const;
  const ZRelocationSetSelectorGroupStats& medium(ZPageAge age) const;
  const ZRelocationSetSelectorGroupStats& large(ZPageAge age) const;

  bool has_relocatable_pages() const;
};

class ZRelocationSetSelectorGroup {
private:
  static constexpr int NumPartitionsShift = 11;
  static constexpr int NumPartitions = int(1) << NumPartitionsShift;

  const char* const                _name;
  const ZPageType                  _page_type;
  const size_t                     _max_page_size;
  const size_t                     _object_size_limit;
  const double                     _fragmentation_limit;
  const size_t                     _page_fragmentation_limit;
  ZArray<ZPage*>                   _live_pages;
  ZArray<ZPage*>                   _not_selected_pages;
  size_t                           _forwarding_entries;
  ZRelocationSetSelectorGroupStats _stats[ZPageAgeCount];

  bool is_disabled();
  bool is_selectable();

  size_t partition_index(const ZPage* page) const;
  void semi_sort();
  void select_inner();

  bool pre_filter_page(const ZPage* page, size_t live_bytes) const;

public:
  ZRelocationSetSelectorGroup(const char* name,
                              ZPageType page_type,
                              size_t max_page_size,
                              size_t object_size_limit,
                              double fragmentation_limit);

  void register_live_page(ZPage* page);
  void register_empty_page(ZPage* page);
  void select();

  const ZArray<ZPage*>* live_pages() const;
  const ZArray<ZPage*>* selected_pages() const;
  const ZArray<ZPage*>* not_selected_pages() const;
  size_t forwarding_entries() const;

  const ZRelocationSetSelectorGroupStats& stats(ZPageAge age) const;
};

class ZRelocationSetSelector : public StackObj {
private:
  ZRelocationSetSelectorGroup _small;
  ZRelocationSetSelectorGroup _medium;
  ZRelocationSetSelectorGroup _large;
  ZArray<ZPage*>              _empty_pages;

  size_t total() const;
  size_t empty() const;
  size_t relocate() const;

public:
  ZRelocationSetSelector(double fragmentation_limit);

  void register_live_page(ZPage* page);
  void register_empty_page(ZPage* page);

  bool should_free_empty_pages(int bulk) const;
  const ZArray<ZPage*>* empty_pages() const;
  void clear_empty_pages();

  void select();

  const ZArray<ZPage*>* selected_small() const;
  const ZArray<ZPage*>* selected_medium() const;

  const ZArray<ZPage*>* not_selected_small() const;
  const ZArray<ZPage*>* not_selected_medium() const;
  const ZArray<ZPage*>* not_selected_large() const;
  size_t forwarding_entries() const;

  ZRelocationSetSelectorStats stats() const;
};

#endif // SHARE_GC_Z_ZRELOCATIONSETSELECTOR_HPP
