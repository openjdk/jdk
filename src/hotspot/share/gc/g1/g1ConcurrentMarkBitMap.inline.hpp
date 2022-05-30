/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CONCURRENTMARKBITMAP_INLINE_HPP
#define SHARE_GC_G1_G1CONCURRENTMARKBITMAP_INLINE_HPP

#include "gc/g1/g1ConcurrentMarkBitMap.hpp"

#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/shared/markBitMap.inline.hpp"
#include "memory/memRegion.hpp"
#include "utilities/align.hpp"
#include "utilities/bitMap.inline.hpp"

inline bool G1CMBitMap::is_marked(oop obj) const { return _bitmap.is_marked(obj); }
inline bool G1CMBitMap::is_marked(HeapWord* addr) const { return _bitmap.is_marked(addr); }

inline bool G1CMBitMap::iterate(MarkBitMapClosure* cl, MemRegion mr) {
  return _bitmap.iterate(cl, mr);
}

inline HeapWord* G1CMBitMap::get_next_marked_addr(const HeapWord* addr,
                                                  HeapWord* const limit) const {
  return _bitmap.get_next_marked_addr(addr, limit);
}

inline void G1CMBitMap::clear(HeapWord* addr) { _bitmap.clear(addr); }
inline void G1CMBitMap::clear(oop obj) { _bitmap.clear(obj); }
inline bool G1CMBitMap::par_mark(HeapWord* addr) { return _bitmap.par_mark(addr); }
inline bool G1CMBitMap::par_mark(oop obj) { return _bitmap.par_mark(obj); }

inline void G1CMBitMap::clear_range(MemRegion mr) {
  _bitmap.clear_range(mr);
}

#endif // SHARE_GC_G1_G1CONCURRENTMARKBITMAP_INLINE_HPP
