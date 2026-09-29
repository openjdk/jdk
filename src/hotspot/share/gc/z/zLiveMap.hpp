/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZLIVEMAP_HPP
#define SHARE_GC_Z_ZLIVEMAP_HPP

#include "gc/z/zAddress.hpp"
#include "gc/z/zBitMap.hpp"
#include "gc/z/zGenerationId.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"

class ObjectClosure;

class ZLiveMap {
  friend class ZLiveMapTest;

private:
  static const uint32_t NumSegments = 64;
  static const uint32_t BitsPerObject = 2;

  const uint32_t    _segment_size;
  const int         _segment_shift;

  Atomic<uint32_t>  _seqnum;
  Atomic<uint32_t>  _live_objects;
  Atomic<size_t>    _live_bytes;
  BitMap::bm_word_t _segment_live_bits;
  BitMap::bm_word_t _segment_claim_bits;
  ZBitMap           _bitmap;

  const BitMapView segment_live_bits() const;
  const BitMapView segment_claim_bits() const;

  BitMapView segment_live_bits();
  BitMapView segment_claim_bits();

  BitMap::idx_t segment_start(BitMap::idx_t segment) const;
  BitMap::idx_t segment_end(BitMap::idx_t segment) const;

  bool is_segment_live(BitMap::idx_t segment) const;
  bool set_segment_live(BitMap::idx_t segment);

  BitMap::idx_t first_live_segment() const;
  BitMap::idx_t next_live_segment(BitMap::idx_t segment) const;
  BitMap::idx_t index_to_segment(BitMap::idx_t index) const;

  bool claim_segment(BitMap::idx_t segment);

  void initialize_bitmap();

  void reset(ZGenerationId id);
  void reset_segment(BitMap::idx_t segment);

  size_t do_object(ObjectClosure* cl, zaddress addr) const;

  template <typename Function>
  void iterate_segment(BitMap::idx_t segment, Function function);

public:
  ZLiveMap(uint32_t object_max_count);
  ZLiveMap(const ZLiveMap& other) = delete;

  void reset();

  bool is_marked(ZGenerationId id) const;

  uint32_t live_objects() const;
  size_t live_bytes() const;

  bool get(ZGenerationId id, BitMap::idx_t index) const;
  bool set(ZGenerationId id, BitMap::idx_t index, bool finalizable, bool& inc_live);

  void inc_live(uint32_t objects, size_t bytes);

  template <typename Function>
  void iterate(ZGenerationId id, Function function);

  BitMap::idx_t find_base_bit(BitMap::idx_t index);
  BitMap::idx_t find_base_bit_in_segment(BitMap::idx_t start, BitMap::idx_t index);
};

#endif // SHARE_GC_Z_ZLIVEMAP_HPP
