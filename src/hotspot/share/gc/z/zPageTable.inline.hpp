/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZPAGETABLE_INLINE_HPP
#define SHARE_GC_Z_ZPAGETABLE_INLINE_HPP

#include "gc/z/zPageTable.hpp"

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGranuleMap.inline.hpp"
#include "gc/z/zIndexDistributor.inline.hpp"
#include "gc/z/zPage.inline.hpp"
#include "gc/z/zPageAllocator.inline.hpp"

#include <limits>

inline int ZPageTable::count() const {
  const size_t size = _map._size;
  assert(size <= std::numeric_limits<int>::max(), "Invalid page table size");

  return static_cast<int>(size);
}

inline ZPage* ZPageTable::get(zaddress addr) const {
  assert(!is_null(addr), "Invalid address");
  return _map.get(ZAddress::offset(addr));
}

inline ZPage* ZPageTable::get(volatile zpointer* p) const {
  return get(to_zaddress((uintptr_t)p));
}

inline ZPage* ZPageTable::at(size_t index) const {
  return _map.at(index);
}

inline ZPageTableIterator::ZPageTableIterator(const ZPageTable* table)
  : _iter(&table->_map),
    _prev(nullptr) {}

inline bool ZPageTableIterator::next(ZPage** page) {
  for (ZPage* entry; _iter.next(&entry);) {
    if (entry != nullptr && entry != _prev) {
      // Next page found
      *page = _prev = entry;
      return true;
    }
  }

  // No more pages
  return false;
}

inline ZPageTableParallelIterator::ZPageTableParallelIterator(const ZPageTable* table)
  : _table(table),
    _index_distributor(table->count()) {}

template <typename Function>
inline void ZPageTableParallelIterator::do_pages(Function function) {
  _index_distributor.do_indices([&](int index) {
    ZPage* const page = _table->at(size_t(index));
    if (page != nullptr) {
      const size_t start_index = untype(page->start()) >> ZGranuleSizeShift;
      if (size_t(index) == start_index) {
        // Next page found
        return function(page);
      }
    }
    return true;
  });
}

inline bool ZGenerationPagesIterator::next(ZPage** page) {
  while (_iterator.next(page)) {
    if ((*page)->generation_id() == _generation_id) {
      return true;
    }
  }

  return false;
}

template <typename Function>
inline void ZGenerationPagesIterator::yield(Function function) {
  _page_allocator->disable_safe_destroy();

  function();

  _page_allocator->enable_safe_destroy();
}

template <typename Function>
inline void ZGenerationPagesParallelIterator::do_pages(Function function) {
  _iterator.do_pages([&](ZPage* page) {
    if (page->generation_id() == _generation_id) {
      return function(page);
    }
    return true;
  });
}

#endif // SHARE_GC_Z_ZPAGETABLE_INLINE_HPP
