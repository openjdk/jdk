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

#ifndef SHARE_GC_Z_ZRANGE_INLINE_HPP
#define SHARE_GC_Z_ZRANGE_INLINE_HPP

#include "gc/z/zRange.hpp"

#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

template <typename Start, typename End>
inline ZRange<Start, End>::ZRange(End start, size_t size, End end)
  : _start(start),
    _size(size) {
  postcond(this->end() == end);
}

template <typename Start, typename End>
inline ZRange<Start, End>::ZRange()
  : _start(End::invalid),
    _size(0) {}

template <typename Start, typename End>
inline ZRange<Start, End>::ZRange(Start start, size_t size)
  : _start(to_end_type(start, 0)),
    _size(size) {}

template <typename Start, typename End>
inline bool ZRange<Start, End>::is_null() const {
  return _start == End::invalid;
}

template <typename Start, typename End>
inline Start ZRange<Start, End>::start() const {
  return to_start_type(_start);
}

template <typename Start, typename End>
inline End ZRange<Start, End>::end() const {
  return _start + _size;
}

template <typename Start, typename End>
inline size_t ZRange<Start, End>::size() const {
  return _size;
}

template <typename Start, typename End>
inline bool ZRange<Start, End>::operator==(const ZRange& other) const {
  precond(!is_null());
  precond(!other.is_null());

  return _start == other._start && _size == other._size;
}

template <typename Start, typename End>
inline bool ZRange<Start, End>::operator!=(const ZRange& other) const {
  return !operator==(other);
}

template <typename Start, typename End>
inline bool ZRange<Start, End>::contains(const ZRange& other) const {
  precond(!is_null());
  precond(!other.is_null());

  return _start <= other._start && other.end() <= end();
}

template <typename Start, typename End>
inline void ZRange<Start, End>::grow_from_front(size_t size) {
  precond(size_t(start()) >= size);

  _start -= size;
  _size  += size;
}

template <typename Start, typename End>
inline void ZRange<Start, End>::grow_from_back(size_t size) {
  _size += size;
}

template <typename Start, typename End>
inline ZRange<Start, End> ZRange<Start, End>::shrink_from_front(size_t size) {
  precond(this->size() >= size);

  _start += size;
  _size  -= size;

  return ZRange(_start - size, size, _start);
}

template <typename Start, typename End>
inline ZRange<Start, End> ZRange<Start, End>::shrink_from_back(size_t size) {
  precond(this->size() >= size);

  _size -= size;

  return ZRange(end(), size, end() + size);
}

template <typename Start, typename End>
inline ZRange<Start, End> ZRange<Start, End>::partition(size_t offset, size_t partition_size) const {
  precond(size() - offset >= partition_size);

  return ZRange(_start + offset, partition_size, _start + offset + partition_size);
}

template <typename Start, typename End>
inline ZRange<Start, End> ZRange<Start, End>::first_part(size_t split_offset) const {
  return partition(0, split_offset);
}

template <typename Start, typename End>
inline ZRange<Start, End> ZRange<Start, End>::last_part(size_t split_offset) const {
  return partition(split_offset, size() - split_offset);
}

template <typename Start, typename End>
inline bool ZRange<Start, End>::adjacent_to(const ZRange<Start, End>& other) const {
  return end() == other.start() || other.end() == start();
}

#endif // SHARE_GC_Z_ZRANGE_INLINE_HPP
