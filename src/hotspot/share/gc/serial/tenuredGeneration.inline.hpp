/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SERIAL_TENUREDGENERATION_INLINE_HPP
#define SHARE_GC_SERIAL_TENUREDGENERATION_INLINE_HPP

#include "gc/serial/tenuredGeneration.hpp"

#include "gc/shared/space.hpp"

inline size_t TenuredGeneration::capacity() const {
  return space()->capacity();
}

inline size_t TenuredGeneration::used() const {
  return space()->used();
}

inline size_t TenuredGeneration::free() const {
  return space()->free();
}

inline bool TenuredGeneration::is_in(const void* p) const {
  return space()->is_in(p);
}

inline void TenuredGeneration::update_for_block(HeapWord* start, HeapWord* end) {
  _bts->update_for_block(start, end);
}

HeapWord* TenuredGeneration::allocate(size_t word_size) {
  HeapWord* res = _the_space->allocate(word_size);
  if (res != nullptr) {
    _bts->update_for_block(res, res + word_size);
  }
  return res;
}

#endif // SHARE_GC_SERIAL_TENUREDGENERATION_INLINE_HPP
