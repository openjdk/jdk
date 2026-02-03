/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_AOTMAPPEDHEAP_HPP
#define SHARE_CDS_AOTMAPPEDHEAP_HPP

#include "cds/aotMapLogger.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"

class AOTMappedHeapHeader {
  size_t           _ptrmap_start_pos; // The first bit in the ptrmap corresponds to this position in the heap.
  size_t           _oopmap_start_pos; // The first bit in the oopmap corresponds to this position in the heap.
  HeapRootSegments _root_segments;    // Heap root segments info

public:
  AOTMappedHeapHeader();
  AOTMappedHeapHeader(size_t ptrmap_start_pos,
                      size_t oopmap_start_pos,
                      HeapRootSegments root_segments);

  size_t ptrmap_start_pos() const { return _ptrmap_start_pos; }
  size_t oopmap_start_pos() const { return _oopmap_start_pos; }
  HeapRootSegments root_segments() const { return _root_segments; }

  // This class is trivially copyable and assignable.
  AOTMappedHeapHeader(const AOTMappedHeapHeader&) = default;
  AOTMappedHeapHeader& operator=(const AOTMappedHeapHeader&) = default;
};

class AOTMappedHeapInfo {
  MemRegion _buffer_region;             // Contains the archived objects to be written into the CDS archive.
  CHeapBitMap _oopmap;
  CHeapBitMap _ptrmap;
  HeapRootSegments _root_segments;
  size_t _oopmap_start_pos;             // How many zeros were removed from the beginning of the bit map?
  size_t _ptrmap_start_pos;             // How many zeros were removed from the beginning of the bit map?

public:
  AOTMappedHeapInfo() :
    _buffer_region(),
    _oopmap(128, mtClassShared),
    _ptrmap(128, mtClassShared),
    _root_segments(),
    _oopmap_start_pos(),
    _ptrmap_start_pos() {}
  bool is_used() { return !_buffer_region.is_empty(); }

  MemRegion buffer_region() { return _buffer_region; }
  void set_buffer_region(MemRegion r) { _buffer_region = r; }

  char* buffer_start() { return (char*)_buffer_region.start(); }
  size_t buffer_byte_size() { return _buffer_region.byte_size();    }

  CHeapBitMap* oopmap() { return &_oopmap; }
  CHeapBitMap* ptrmap() { return &_ptrmap; }

  void set_oopmap_start_pos(size_t start_pos) { _oopmap_start_pos = start_pos; }
  void set_ptrmap_start_pos(size_t start_pos) { _ptrmap_start_pos = start_pos; }

  void set_root_segments(HeapRootSegments segments) { _root_segments = segments; };
  HeapRootSegments root_segments() { return _root_segments; }

  AOTMappedHeapHeader create_header();
};

#if INCLUDE_CDS_JAVA_HEAP
class AOTMappedHeapOopIterator : public AOTMapLogger::OopDataIterator {
protected:
  address _current;
  address _next;

  address _buffer_start;
  address _buffer_end;
  uint64_t _buffer_start_narrow_oop;
  intptr_t _buffer_to_requested_delta;
  int _requested_shift;

  size_t _num_root_segments;
  size_t _num_obj_arrays_logged;

public:
  AOTMappedHeapOopIterator(address buffer_start,
                           address buffer_end,
                           address requested_base,
                           address requested_start,
                           int requested_shift,
                           size_t num_root_segments)
    : _current(nullptr),
      _next(buffer_start),
      _buffer_start(buffer_start),
      _buffer_end(buffer_end),
      _requested_shift(requested_shift),
      _num_root_segments(num_root_segments),
      _num_obj_arrays_logged(0) {
    _buffer_to_requested_delta = requested_start - buffer_start;
    _buffer_start_narrow_oop = 0xdeadbeed;
    if (UseCompressedOops) {
      _buffer_start_narrow_oop = (uint64_t)(pointer_delta(requested_start, requested_base, 1)) >> requested_shift;
      assert(_buffer_start_narrow_oop < 0xffffffff, "sanity");
    }
  }

  virtual AOTMapLogger::OopData capture(address buffered_addr) = 0;

  bool has_next() override {
    return _next < _buffer_end;
  }

  AOTMapLogger::OopData next() override {
    _current = _next;
    AOTMapLogger::OopData result = capture(_current);
    if (result._klass->is_objArray_klass()) {
      result._is_root_segment = _num_obj_arrays_logged++ < _num_root_segments;
    }
    _next = _current + result._size * BytesPerWord;
    return result;
  }

  AOTMapLogger::OopData obj_at(narrowOop* addr) override {
    uint64_t n = (uint64_t)(*addr);
    if (n == 0) {
      return null_data();
    } else {
      precond(n >= _buffer_start_narrow_oop);
      address buffer_addr = _buffer_start + ((n - _buffer_start_narrow_oop) << _requested_shift);
      return capture(buffer_addr);
    }
  }

  AOTMapLogger::OopData obj_at(oop* addr) override {
    address requested_value = cast_from_oop<address>(*addr);
    if (requested_value == nullptr) {
      return null_data();
    } else {
      address buffer_addr = requested_value - _buffer_to_requested_delta;
      return capture(buffer_addr);
    }
  }

  GrowableArrayCHeap<AOTMapLogger::OopData, mtClass>* roots() override {
    return new GrowableArrayCHeap<AOTMapLogger::OopData, mtClass>();
  }
};
#endif // INCLUDE_CDS_JAVA_HEAP

#endif // SHARE_CDS_AOTMAPPEDHEAP_HPP
