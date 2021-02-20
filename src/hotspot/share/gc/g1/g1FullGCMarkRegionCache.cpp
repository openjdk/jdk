/*
 * Copyright (c) 2021, Huawei Technologies Co. Ltd. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Alibaba designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

#include "precompiled.hpp"
#include "services/memTracker.hpp"
#include "gc/g1/g1FullGCMarkRegionCache.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "runtime/atomic.hpp"

G1FullGCMarkRegionCache::G1FullGCMarkRegionCache() {
  _cache = NEW_C_HEAP_ARRAY(size_t, G1CollectedHeap::heap()->max_regions(), mtGC);
  memset(_cache, 0 , sizeof(size_t)*G1CollectedHeap::heap()->max_regions());
}
void G1FullGCMarkRegionCache::inc_live(uint hr_index, size_t words) {
  _cache[hr_index] += words;
}

void* G1FullGCMarkRegionCache::operator new(size_t size) {
  return (address)AllocateHeap(size, mtGC, CURRENT_PC, AllocFailStrategy::RETURN_NULL);
}

void G1FullGCMarkRegionCache::operator delete(void* p) {
  FreeHeap(p);
}

G1FullGCMarkRegionCache::~G1FullGCMarkRegionCache() {
  for (uint i = 0; i < G1CollectedHeap::heap()->max_regions(); ++i) {
    if (_cache[i]) {
      Atomic::add(G1CollectedHeap::heap()->region_at(i)->live_words_after_full_gc_mark_addr(), _cache[i]);
    }
  }
  FREE_C_HEAP_ARRAY(size_t, _cache);
}