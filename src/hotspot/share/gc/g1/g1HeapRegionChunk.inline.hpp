/*
 * Copyright (c) 2022, Huawei Technologies Co. Ltd. All rights reserved.
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
 *
 */

#ifndef SHARE_GC_G1_G1HEAPREGIONCHUNK_INLINE_HPP
#define SHARE_GC_G1_G1HEAPREGIONCHUNK_INLINE_HPP

#include "gc/g1/g1HeapRegionChunk.hpp"
#include "gc/shared/markBitMap.inline.hpp"
#include "runtime/prefetch.hpp"

template<typename ApplyToMarkedClosure>
inline void G1HeapRegionChunk::apply_to_marked_objects(ApplyToMarkedClosure* closure) {
  HeapWord* next_addr = _first_obj_in_chunk;

  while (next_addr < _limit) {
    Prefetch::write(next_addr, PrefetchScanIntervalInBytes);
    // This explicit is_marked check is a way to avoid
    // some extra work done by get_next_marked_addr for
    // the case where next_addr is marked.
    if (_bitmap->is_marked(next_addr)) {
      oop current = cast_to_oop(next_addr);
      next_addr += closure->apply(current);
    } else {
      next_addr = _bitmap->get_next_marked_addr(next_addr, _limit);
    }
  }
}

#endif //SHARE_GC_G1_G1HEAPREGIONCHUNK_INLINE_HPP
