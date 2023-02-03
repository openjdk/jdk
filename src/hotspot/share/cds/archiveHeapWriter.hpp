/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_ARCHIVEHEAPWRITER_HPP
#define SHARE_CDS_ARCHIVEHEAPWRITER_HPP

#include "cds/heapShared.hpp"
#include "memory/allocation.hpp"
#include "memory/allStatic.hpp"
#include "oops/oopHandle.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/macros.hpp"
#include "utilities/resourceHash.hpp"

#if INCLUDE_CDS_JAVA_HEAP

struct ArchiveHeapBitmapInfo;
class MemRegion;

class ArchiveHeapWriter : AllStatic {
  class EmbeddedOopRelocator;
  struct NativePointerInfo {
    oop _src_obj;
    int _field_offset;
  };

  // The minimum region size of all collectors that are supported by CDS in
  // ArchiveHeapLoader::can_map() mode. Currently only G1 is supported. G1's region size
  // depends on -Xmx, but can never be smaller than 1 * M.
  // (TODO: Perhaps change to 256K to be compatible with Shenandoah)
  static constexpr int MIN_GC_REGION_ALIGNMENT = 1 * M;

  // "source" vs "buffered" vs "requested"
  //
  // [1] HeapShared::archive_objects() identifies all of the oops that need to be stored
  //     into the CDS archive. These are entered into HeapShared::archived_object_cache().
  //     These are called "source objects"
  //
  // [2] ArchiveHeapWriter::write() copies all source objects into ArchiveHeapWriter::_buffer,
  //     which is a GrowableArray that sites outside of the valid heap range. Therefore
  //     we avoid using the addresses of these copies as oops. They are usually
  //     called "buffered_addr" in the code (of the type "address").
  //
  // [3] Each archived object has a "requested address" -- at run time, if the object
  //     can be mapped at this address, we can avoid relocation.
  //
  // Note: the design and convention is the same as for the archiving of Metaspace objects.
  // See archiveBuilder.hpp.

  static GrowableArrayCHeap<u1, mtClassShared>* _buffer;

  // The exclusive top of the last object that has been copied into this->_buffer.
  static size_t _buffer_top;

  // The bounds of the open region inside this->_buffer.
  static size_t _open_bottom;  // inclusive
  static size_t _open_top;     // exclusive

  // The bounds of the closed region inside this->_buffer.
  static size_t _closed_bottom;  // inclusive
  static size_t _closed_top;     // exclusive

  // The bottom of the copy of Heap::roots() inside this->_buffer.
  static size_t _heap_roots_bottom;
  static size_t _heap_roots_word_size;

  static address _requested_open_region_bottom;
  static address _requested_open_region_top;
  static address _requested_closed_region_bottom;
  static address _requested_closed_region_top;

  static ResourceBitMap* _closed_oopmap;
  static ResourceBitMap* _open_oopmap;

  static ArchiveHeapBitmapInfo _closed_oopmap_info;
  static ArchiveHeapBitmapInfo _open_oopmap_info;

  static GrowableArrayCHeap<NativePointerInfo, mtClassShared>* _native_pointers;
  static GrowableArrayCHeap<oop, mtClassShared>* _source_objs;

  typedef ResourceHashtable<size_t, oop,
      36137, // prime number
      AnyObj::C_HEAP,
      mtClassShared> BufferOffsetToSourceObjectTable;
  static BufferOffsetToSourceObjectTable* _buffer_offset_to_source_obj_table;

  static void allocate_buffer();
  static void ensure_buffer_space(size_t min_bytes);

  // Both Java bytearray and GrowableArraty use int indices and lengths. Do a safe typecast with range check
  static int to_array_index(size_t i) {
    assert(i <= (size_t)max_jint, "must be");
    return (size_t)i;
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

  static address buffer_top() {
    return buffer_bottom() + _buffer_top;
  }

  static bool in_buffer(address buffered_addr) {
    return (buffer_bottom() <= buffered_addr) && (buffered_addr < buffer_top());
  }

  static size_t buffered_address_to_offset(address buffered_addr) {
    assert(in_buffer(buffered_addr), "sanity");
    return buffered_addr - buffer_bottom();
  }

  static void copy_roots_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots);
  static void copy_source_objs_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots);
  static void copy_source_objs_to_buffer_by_region(bool copy_open_region);
  static size_t copy_one_source_obj_to_buffer(oop src_obj);

  static void maybe_fill_gc_region_gap(size_t required_byte_size);
  static size_t filler_array_byte_size(int length);
  static int filler_array_length(size_t fill_bytes);
  static void init_filler_array_at_buffer_top(int array_length, size_t fill_bytes);

  static void set_requested_address_for_regions(GrowableArray<MemRegion>* closed_regions,
                                                GrowableArray<MemRegion>* open_regions);
  static void relocate_embedded_oops(GrowableArrayCHeap<oop, mtClassShared>* roots,
                                     GrowableArray<ArchiveHeapBitmapInfo>* closed_bitmaps,
                                     GrowableArray<ArchiveHeapBitmapInfo>* open_bitmaps);
  static ArchiveHeapBitmapInfo compute_ptrmap(bool is_open);
  static ArchiveHeapBitmapInfo make_bitmap_info(ResourceBitMap* bitmap, bool is_open,  bool is_oopmap);
  static bool is_in_requested_regions(oop o);
  static oop requested_obj_from_buffer_offset(size_t offset);

  static oop load_oop_from_buffer(oop* buffered_addr);
  static oop load_oop_from_buffer(narrowOop* buffered_addr);
  static void store_oop_in_buffer(oop* buffered_addr, oop requested_obj);
  static void store_oop_in_buffer(narrowOop* buffered_addr, oop requested_obj);

  template <typename T> static oop load_source_oop_from_buffer(T* buffered_addr);
  template <typename T> static void store_requested_oop_in_buffer(T* buffered_addr, oop request_oop);

  template <typename T> static T* requested_addr_to_buffered_addr(T* p);
  template <typename T> static void relocate_field_in_buffer(T* field_addr_in_buffer);
  template <typename T> static void mark_oop_pointer(T* buffered_addr);
  template <typename T> static void relocate_root_at(oop requested_roots, int index);

  static void update_header_for_requested_obj(oop requested_obj, oop src_obj, Klass* src_klass);
public:
  static void init() NOT_CDS_JAVA_HEAP_RETURN;
  static void add_source_obj(oop src_obj);
  static bool is_too_large_to_archive(size_t size);
  static bool is_too_large_to_archive(oop obj);
  static bool is_string_too_large_to_archive(oop string);
  static void write(GrowableArrayCHeap<oop, mtClassShared>*,
                    GrowableArray<MemRegion>* closed_regions, GrowableArray<MemRegion>* open_regions,
                    GrowableArray<ArchiveHeapBitmapInfo>* closed_bitmaps,
                    GrowableArray<ArchiveHeapBitmapInfo>* open_bitmaps);
  static address heap_region_requested_bottom(int heap_region_idx);
  static oop heap_roots_requested_address();
  static address buffered_heap_roots_addr() {
    return offset_to_buffered_address<address>(_heap_roots_bottom);
  }
  static size_t heap_roots_word_size() {
    return _heap_roots_word_size;
  }

  static void mark_native_pointer(oop src_obj, int offset);
  static oop source_obj_to_requested_obj(oop src_obj);
  static oop buffered_addr_to_source_obj(address buffered_addr);
  static address buffered_addr_to_requested_addr(address buffered_addr);
};
#endif // INCLUDE_CDS_JAVA_HEAP
#endif // SHARE_CDS_ARCHIVEHEAPWRITER_HPP
