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

#ifndef SHARE_GC_Z_ZRELOCATIONSETSELECTOR_INLINE_HPP
#define SHARE_GC_Z_ZRELOCATIONSETSELECTOR_INLINE_HPP

#include "gc/z/zRelocationSetSelector.hpp"

inline size_t ZRelocationSetSelectorGroupStats::npages() const {
  return _npages;
}

inline size_t ZRelocationSetSelectorGroupStats::total() const {
  return _total;
}

inline size_t ZRelocationSetSelectorGroupStats::live() const {
  return _live;
}

inline size_t ZRelocationSetSelectorGroupStats::garbage() const {
  return _garbage;
}

inline size_t ZRelocationSetSelectorGroupStats::empty() const {
  return _empty;
}

inline size_t ZRelocationSetSelectorGroupStats::compacting_from() const {
  return _compacting_from;
}

inline size_t ZRelocationSetSelectorGroupStats::compacting_to() const {
  return _compacting_to;
}

inline const ZRelocationSetSelectorGroupStats& ZRelocationSetSelectorStats::small() const {
  return _small;
}

inline const ZRelocationSetSelectorGroupStats& ZRelocationSetSelectorStats::medium() const {
  return _medium;
}

inline const ZRelocationSetSelectorGroupStats& ZRelocationSetSelectorStats::large() const {
  return _large;
}

inline ZPage* const* ZRelocationSetSelectorGroup::selected() const {
  return _sorted_pages;
}

inline size_t ZRelocationSetSelectorGroup::nselected() const {
  return _nselected;
}

inline const ZRelocationSetSelectorGroupStats& ZRelocationSetSelectorGroup::stats() const {
  return _stats;
}

inline size_t ZRelocationSetSelector::total() const {
  return _small.stats().total() + _medium.stats().total() + _large.stats().total();
}

inline size_t ZRelocationSetSelector::empty() const {
  return _small.stats().empty() + _medium.stats().empty() + _large.stats().empty();
}

inline size_t ZRelocationSetSelector::compacting_from() const {
  return _small.stats().compacting_from() + _medium.stats().compacting_from() + _large.stats().compacting_from();
}

inline size_t ZRelocationSetSelector::compacting_to() const {
  return _small.stats().compacting_to() + _medium.stats().compacting_to() + _large.stats().compacting_to();
}

#endif // SHARE_GC_Z_ZRELOCATIONSETSELECTOR_INLINE_HPP
