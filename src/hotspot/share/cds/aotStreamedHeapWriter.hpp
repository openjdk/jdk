/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_AOTSTREAMEDHEAPWRITER_HPP
#define SHARE_CDS_AOTSTREAMEDHEAPWRITER_HPP

#include "cds/aotMapLogger.hpp"
#include "cds/heapShared.hpp"
#include "memory/allocation.hpp"
#include "memory/allStatic.hpp"
#include "oops/oopHandle.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"
#include "utilities/resizableHashTable.hpp"

class MemRegion;

#if INCLUDE_CDS_JAVA_HEAP
class AOTStreamedHeapWriter : AllStatic {
  class EmbeddedOopMapper;
  static GrowableArrayCHeap<u1, mtClassShared>* _buffer;

  // The number of bytes that have written into _buffer (may be smaller than _buffer->length()).
  static size_t _buffer_used;

  // The bottom of the copy of Heap::roots() inside this->_buffer.
  static size_t _roots_offset;

  // Offset to the forwarding information
  static size_t _forwarding_offset;

  // Offset to dfs bounds information
  static size_t _root_highest_object_index_table_offset;

  static GrowableArrayCHeap<oop, mtClassShared>* _source_objs;

  typedef ResizeableHashTable<size_t, OopHandle,
                              AnyObj::C_HEAP,
                              mtClassShared> BufferOffsetToSourceObjectTable;

  static BufferOffsetToSourceObjectTable* _buffer_offset_to_source_obj_table;

  typedef ResizeableHashTable<void*, int,
                              AnyObj::C_HEAP,
                              mtClassShared> SourceObjectToDFSOrderTable;
  static SourceObjectToDFSOrderTable* _dfs_order_table;

  static int* _roots_highest_dfs;
  static size_t* _dfs_to_archive_object_table;

  static int cmp_dfs_order(oop* o1, oop* o2);

  static void allocate_buffer();
  static void ensure_buffer_space(size_t min_bytes);

  // Both Java bytearray and GrowableArraty use int indices and lengths. Do a safe typecast with range check
  static int to_array_index(size_t i) {
    assert(i <= (size_t)max_jint, "must be");
    return (int)i;
  }
  static int to_array_length(size_t n) {
    return to_array_index(n);
  }

  template <typename T> static T offset_to_buffered_address(size_t offset) {
    return (T)(_buffer->adr_at(to_array_index(offset)));
  }

  static address buffer_bottom() {
    return offset_to_buffered_address<address>(0);
  }

  // The exclusive end of the last object that was copied into the buffer.
  static address buffer_top() {
    return buffer_bottom() + _buffer_used;
  }

  static bool in_buffer(address buffered_addr) {
    return (buffer_bottom() <= buffered_addr) && (buffered_addr < buffer_top());
  }

  static size_t buffered_address_to_offset(address buffered_addr) {
    assert(in_buffer(buffered_addr), "sanity");
    return buffered_addr - buffer_bottom();
  }

  static void order_source_objs(GrowableArrayCHeap<oop, mtClassShared>* roots);
  static void copy_roots_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots);
  static void copy_source_objs_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots);
  static size_t copy_one_source_obj_to_buffer(oop src_obj);

  template <typename T>
  static void write(T value);
  static void copy_forwarding_to_buffer();
  static void copy_roots_max_dfs_to_buffer(int roots_length);

  static void map_embedded_oops(AOTStreamedHeapInfo* info);
  static bool is_in_requested_range(oop o);
  static oop requested_obj_from_buffer_offset(size_t offset);

  static oop load_oop_from_buffer(oop* buffered_addr);
  static oop load_oop_from_buffer(narrowOop* buffered_addr);
  inline static void store_oop_in_buffer(oop* buffered_addr, int dfs_index);
  inline static void store_oop_in_buffer(narrowOop* buffered_addr, int dfs_index);

  template <typename T> static void mark_oop_pointer(T* buffered_addr, CHeapBitMap* oopmap);
  template <typename T> static void map_oop_field_in_buffer(oop obj, T* field_addr_in_buffer, CHeapBitMap* oopmap);

  static void update_header_for_buffered_addr(address buffered_addr, oop src_obj, Klass* src_klass);

  static void populate_archive_heap_info(AOTStreamedHeapInfo* info);

public:
  static void init() NOT_CDS_JAVA_HEAP_RETURN;

  static void delete_tables_with_raw_oops();
  static void add_source_obj(oop src_obj);
  static void write(GrowableArrayCHeap<oop, mtClassShared>*, AOTStreamedHeapInfo* heap_info);
  static address buffered_heap_roots_addr() {
    return offset_to_buffered_address<address>(_roots_offset);
  }

  static size_t buffered_addr_to_buffered_offset(address buffered_addr) {
    assert(buffered_addr != nullptr, "should not be null");
    return size_t(buffered_addr) - size_t(buffer_bottom());
  }

  static bool is_dumped_interned_string(oop obj);

  static size_t source_obj_to_buffered_offset(oop src_obj);
  static address source_obj_to_buffered_addr(oop src_obj);

  static oop buffered_offset_to_source_obj(size_t buffered_offset);
  static oop buffered_addr_to_source_obj(address buffered_addr);

  static AOTMapLogger::OopDataIterator* oop_iterator(AOTStreamedHeapInfo* heap_info);
};
#endif // INCLUDE_CDS_JAVA_HEAP
#endif // SHARE_CDS_AOTSTREAMEDHEAPWRITER_HPP
