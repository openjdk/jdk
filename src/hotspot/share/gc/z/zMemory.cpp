/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zList.inline.hpp"
#include "gc/z/zMemory.inline.hpp"
#include "memory/allocation.inline.hpp"

uintptr_t ZMemoryManager::alloc_from_front(size_t size) {
  ZListIterator<ZMemory> iter(&_freelist);
  for (ZMemory* area; iter.next(&area);) {
    if (area->size() >= size) {
      if (area->size() == size) {
        // Exact match, remove area
        const uintptr_t start = area->start();
        _freelist.remove(area);
        delete area;
        return start;
      } else {
        // Larger than requested, shrink area
        const uintptr_t start = area->start();
        area->shrink_from_front(size);
        return start;
      }
    }
  }

  // Out of memory
  return UINTPTR_MAX;
}

uintptr_t ZMemoryManager::alloc_from_back(size_t size) {
  ZListReverseIterator<ZMemory> iter(&_freelist);
  for (ZMemory* area; iter.next(&area);) {
    if (area->size() >= size) {
      if (area->size() == size) {
        // Exact match, remove area
        const uintptr_t start = area->start();
        _freelist.remove(area);
        delete area;
        return start;
      } else {
        // Larger than requested, shrink area
        area->shrink_from_back(size);
        return area->end();
      }
    }
  }

  // Out of memory
  return UINTPTR_MAX;
}

void ZMemoryManager::free(uintptr_t start, size_t size) {
  assert(start != UINTPTR_MAX, "Invalid address");
  const uintptr_t end = start + size;

  ZListIterator<ZMemory> iter(&_freelist);
  for (ZMemory* area; iter.next(&area);) {
    if (start < area->start()) {
      ZMemory* const prev = _freelist.prev(area);
      if (prev != NULL && start == prev->end()) {
        if (end == area->start()) {
          // Merge with prev and current area
          prev->grow_from_back(size + area->size());
          _freelist.remove(area);
          delete area;
        } else {
          // Merge with prev area
          prev->grow_from_back(size);
        }
      } else if (end == area->start()) {
        // Merge with current area
        area->grow_from_front(size);
      } else {
        // Insert new area before current area
        assert(end < area->start(), "Areas must not overlap");
        ZMemory* new_area = new ZMemory(start, size);
        _freelist.insert_before(area, new_area);
      }

      // Done
      return;
    }
  }

  // Insert last
  ZMemory* const last = _freelist.last();
  if (last != NULL && start == last->end()) {
    // Merge with last area
    last->grow_from_back(size);
  } else {
    // Insert new area last
    ZMemory* new_area = new ZMemory(start, size);
    _freelist.insert_last(new_area);
  }
}
