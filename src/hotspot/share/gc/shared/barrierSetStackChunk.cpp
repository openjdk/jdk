/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/barrierSetStackChunk.hpp"
#include "memory/iterator.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopsHierarchy.hpp"
#include "oops/stackChunkOop.inline.hpp"
#include "runtime/globals.hpp"
#include "utilities/debug.hpp"

class UncompressOopsOopClosure : public OopClosure {
public:
  void do_oop(oop* p) override {
    assert(UseCompressedOops, "Only needed with compressed oops");
    oop obj = CompressedOops::decode(*(narrowOop*)p);
    assert(obj == nullptr || dbg_is_good_oop(obj), "p: " INTPTR_FORMAT " obj: " INTPTR_FORMAT, p2i(p), p2i((oopDesc*)obj));
    *p = obj;
  }

  void do_oop(narrowOop* p) override {}
};

class CompressOopsOopClosure : public OopClosure {
  stackChunkOop _chunk;
  BitMapView _bm;

  void convert_oop_to_narrowOop(oop* p) {
    oop obj = *p;
    *p = nullptr;
    *(narrowOop*)p = CompressedOops::encode(obj);
  }

  template <typename T>
  void do_oop_work(T* p) {
    BitMap::idx_t index = _chunk->bit_index_for(p);
    assert(!_bm.at(index), "must not be set already");
    _bm.set_bit(index);
  }

public:
  CompressOopsOopClosure(stackChunkOop chunk)
    : _chunk(chunk), _bm(chunk->bitmap()) {}

  virtual void do_oop(oop* p) override {
    if (UseCompressedOops) {
      // Convert all oops to narrow before marking the oop in the bitmap.
      convert_oop_to_narrowOop(p);
      do_oop_work((narrowOop*)p);
    } else {
      do_oop_work(p);
    }
  }

  virtual void do_oop(narrowOop* p) override {
    do_oop_work(p);
  }
};

void BarrierSetStackChunk::encode_gc_mode(stackChunkOop chunk, OopIterator* iterator) {
  CompressOopsOopClosure cl(chunk);
  iterator->oops_do(&cl);
}

void BarrierSetStackChunk::decode_gc_mode(stackChunkOop chunk, OopIterator* iterator) {
  if (chunk->has_bitmap() && UseCompressedOops) {
    UncompressOopsOopClosure cl;
    iterator->oops_do(&cl);
  }
}

oop BarrierSetStackChunk::load_oop(stackChunkOop chunk, oop* addr) {
  return RawAccess<>::oop_load(addr);
}

oop BarrierSetStackChunk::load_oop(stackChunkOop chunk, narrowOop* addr) {
  return RawAccess<>::oop_load(addr);
}
