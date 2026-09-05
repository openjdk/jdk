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

#ifndef SHARE_CDS_AOTSTREAMEDHEAP_HPP
#define SHARE_CDS_AOTSTREAMEDHEAP_HPP

#include "cds/aotMapLogger.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"

class AOTStreamedHeapHeader {
  size_t _forwarding_offset;                      // Offset of forwarding information in the heap region.
  size_t _roots_offset;                           // Start position for the roots
  size_t _root_highest_object_index_table_offset; // Offset of root dfs depth information
  size_t _num_roots;                              // Number of embedded roots
  size_t _num_archived_objects;                   // The number of archived heap objects

public:
  AOTStreamedHeapHeader();
  AOTStreamedHeapHeader(size_t forwarding_offset,
                        size_t roots_offset,
                        size_t num_roots,
                        size_t root_highest_object_index_table_offset,
                        size_t num_archived_objects);

  size_t forwarding_offset() const { return _forwarding_offset; }
  size_t roots_offset() const { return _roots_offset; }
  size_t num_roots() const { return _num_roots; }
  size_t root_highest_object_index_table_offset() const { return _root_highest_object_index_table_offset; }
  size_t num_archived_objects() const { return _num_archived_objects; }

  // This class is trivially copyable and assignable.
  AOTStreamedHeapHeader(const AOTStreamedHeapHeader&) = default;
  AOTStreamedHeapHeader& operator=(const AOTStreamedHeapHeader&) = default;
};

class AOTStreamedHeapInfo {
  MemRegion _buffer_region;             // Contains the archived objects to be written into the CDS archive.
  CHeapBitMap _oopmap;
  size_t _roots_offset;                 // Offset of the HeapShared::roots() object, from the bottom
                                        // of the archived heap objects, in bytes.
  size_t _num_roots;

  size_t _forwarding_offset;            // Offset of forwarding information from the bottom
  size_t _root_highest_object_index_table_offset; // Offset to root dfs depth information
  size_t _num_archived_objects;         // The number of archived objects written into the CDS archive.

public:
  AOTStreamedHeapInfo()
    : _buffer_region(),
      _oopmap(128, mtClassShared),
      _roots_offset(),
      _forwarding_offset(),
      _root_highest_object_index_table_offset(),
      _num_archived_objects() {}

  bool is_used() { return !_buffer_region.is_empty(); }

  void set_buffer_region(MemRegion r) { _buffer_region = r; }
  MemRegion buffer_region() { return _buffer_region; }
  char* buffer_start() { return (char*)_buffer_region.start(); }
  size_t buffer_byte_size() { return _buffer_region.byte_size();    }

  CHeapBitMap* oopmap() { return &_oopmap; }
  void set_roots_offset(size_t n) { _roots_offset = n; }
  size_t roots_offset() { return _roots_offset; }
  void set_num_roots(size_t n) { _num_roots = n; }
  size_t num_roots() { return _num_roots; }
  void set_forwarding_offset(size_t n) { _forwarding_offset = n; }
  void set_root_highest_object_index_table_offset(size_t n) { _root_highest_object_index_table_offset = n; }
  void set_num_archived_objects(size_t n) { _num_archived_objects = n; }
  size_t num_archived_objects() { return _num_archived_objects; }

  AOTStreamedHeapHeader create_header();
};

#if INCLUDE_CDS_JAVA_HEAP
class AOTStreamedHeapOopIterator : public AOTMapLogger::OopDataIterator {
protected:
  int _current;
  int _next;
  address _buffer_start;
  int _num_archived_objects;

public:
  AOTStreamedHeapOopIterator(address buffer_start,
                             int num_archived_objects)
    : _current(0),
      _next(1),
      _buffer_start(buffer_start),
      _num_archived_objects(num_archived_objects) {}

  virtual AOTMapLogger::OopData capture(int dfs_index)  = 0;

  bool has_next() override {
    return _next <= _num_archived_objects;
  }

  AOTMapLogger::OopData next() override {
    _current = _next;
    AOTMapLogger::OopData result = capture(_current);
    _next = _current + 1;
    return result;
  }

  AOTMapLogger::OopData obj_at(narrowOop* addr) override {
    int dfs_index = (int)(*addr);
    if (dfs_index == 0) {
      return null_data();
    } else {
      return capture(dfs_index);
    }
  }

  AOTMapLogger::OopData obj_at(oop* addr) override {
    int dfs_index = (int)cast_from_oop<uintptr_t>(*addr);
    if (dfs_index == 0) {
      return null_data();
    } else {
      return capture(dfs_index);
    }
  }
};
#endif // INCLUDE_CDS_JAVA_HEAP

#endif // SHARE_CDS_AOTSTREAMEDHEAP_HPP
