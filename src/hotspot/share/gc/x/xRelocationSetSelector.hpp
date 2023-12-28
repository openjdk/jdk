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

#ifndef SHARE_GC_X_XRELOCATIONSETSELECTOR_HPP
#define SHARE_GC_X_XRELOCATIONSETSELECTOR_HPP

#include "gc/x/xArray.hpp"
#include "memory/allocation.hpp"

class XPage;

class XRelocationSetSelectorGroupStats {
  friend class XRelocationSetSelectorGroup;

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
  XRelocationSetSelectorGroupStats();

  size_t npages_candidates() const;
  size_t total() const;
  size_t live() const;
  size_t empty() const;

  size_t npages_selected() const;
  size_t relocate() const;
};

class XRelocationSetSelectorStats {
  friend class XRelocationSetSelector;

private:
  XRelocationSetSelectorGroupStats _small;
  XRelocationSetSelectorGroupStats _medium;
  XRelocationSetSelectorGroupStats _large;

public:
  const XRelocationSetSelectorGroupStats& small() const;
  const XRelocationSetSelectorGroupStats& medium() const;
  const XRelocationSetSelectorGroupStats& large() const;
};

class XRelocationSetSelectorGroup {
private:
  const char* const                _name;
  const uint8_t                    _page_type;
  const size_t                     _page_size;
  const size_t                     _object_size_limit;
  const size_t                     _fragmentation_limit;
  XArray<XPage*>                   _live_pages;
  size_t                           _forwarding_entries;
  XRelocationSetSelectorGroupStats _stats;

  bool is_disabled();
  bool is_selectable();
  void semi_sort();
  void select_inner();

public:
  XRelocationSetSelectorGroup(const char* name,
                              uint8_t page_type,
                              size_t page_size,
                              size_t object_size_limit);

  void register_live_page(XPage* page);
  void register_empty_page(XPage* page);
  void select();

  const XArray<XPage*>* selected() const;
  size_t forwarding_entries() const;

  const XRelocationSetSelectorGroupStats& stats() const;
};

class XRelocationSetSelector : public StackObj {
private:
  XRelocationSetSelectorGroup _small;
  XRelocationSetSelectorGroup _medium;
  XRelocationSetSelectorGroup _large;
  XArray<XPage*>              _empty_pages;

  size_t total() const;
  size_t empty() const;
  size_t relocate() const;

public:
  XRelocationSetSelector();

  void register_live_page(XPage* page);
  void register_empty_page(XPage* page);

  bool should_free_empty_pages(int bulk) const;
  const XArray<XPage*>* empty_pages() const;
  void clear_empty_pages();

  void select();

  const XArray<XPage*>* small() const;
  const XArray<XPage*>* medium() const;
  size_t forwarding_entries() const;

  XRelocationSetSelectorStats stats() const;
};

#endif // SHARE_GC_X_XRELOCATIONSETSELECTOR_HPP
