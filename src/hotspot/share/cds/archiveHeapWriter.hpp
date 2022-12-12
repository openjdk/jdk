/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/macros.hpp"
#include "utilities/resourceHash.hpp"

#if INCLUDE_CDS_JAVA_HEAP

struct ArchiveHeapBitmapInfo;
class MemRegion;
template<class E> class GrowableArray;
template <typename E, MEMFLAGS F> class GrowableArrayCHeap;

class ArchiveHeapWriter : AllStatic {
  class EmbeddedOopRelocator;
  struct NativePointerInfo {
    oop _orig_obj;
    int _field_offset;
  };

  // this->_buffer cannot contain more than this number of bytes.
  static constexpr int MAX_OUTPUT_BYTES = (int)max_jint;

  // The minimum region size of all collectors that are supported by CDS in
  // ArchiveHeapLoader::can_map() mode. Currently only G1 is supported. G1's region size
  // depends on -Xmx, but can never be smaller than 1 * M.
  static constexpr int MIN_GC_REGION_ALIGNMENT = 1 * M;

  // TEMP notes: What are these?
  //
  //                              In heap range   Real Object   Modifiable
  //     oop orig_obj;               Y               Y             Y (but you shouldn't)
  //     oop buffer_obj;             Y               N             Y
  //     oop output_obj;             N               N             Y
  //     oop requested_obj;          Y               N             N
  //
  // Because of issues like JDK-8297914, we need to make an extra pass on the objects:
  //
  // [1] HeapShared::archive_object() gets an orig_oop, which is a real Java object that
  //     we want to store into CDS.
  //
  // [2] We first copy the orig_oop into _buffer, which gives us a buffered_oop. This isn't
  //     really an oop, as it points to the interior of a byte array, and is not part of the
  //     parseable heap. Nevertheless, we use these as regular oops in places like
  //     java_lang_Class::process_archived_mirror() and don't see any problems. Nevertheless,
  //     this is a hack and will be removed after JDK-8297914.
  //
  // [3] We make a second pass and copy all buffered_objs into the _output. This gives us
  //     output_objs. These really aren't oops, as their locations are outside of the Java heap.
  //     We still use some oopDesc operations on them, but these should be eliminated before we integrate ... (FIXME)
  //
  // [4] When patching the oop fields stored inside the output_objs, we use the requested
  //     addresses (requested_objs) of the referents. Writing into a requested_objs will
  //     usually give you a SEGV as it points to the top of the heap, which most likely is
  //     not yet committed.
  //
  // TODO: when ArchiveHeapWriter::_buffer is eliminated, we will rename
  //           orig_obj              -> source_obj
  //           {_output, output_obj} -> {_buffer, buffer_obj}.
  // This way it will be consistent with the naming convention for the metadata objects (see archiveBuilder.hpp)


  static OopHandle _buffer; // a Java byte array

  // The exclusive end of the last object that has been copied into this->_buffer'
  static int _buffer_top;

  static GrowableArrayCHeap<u1, mtClassShared>* _output;

  // The exclusive top of the last object that has been copied into this->_output.
  static int _output_top;

  // The bounds of the open region inside this->_output.
  static int _open_bottom;  // inclusive
  static int _open_top;     // exclusive

  // The bounds of the closed region inside this->_output.
  static int _closed_bottom;  // inclusive
  static int _closed_top;     // exclusive

  // The bottom of the copy of Heap::roots() inside this->_output.
  static int _heap_roots_bottom;

  // TODO comment ...
  static address _requested_open_region_bottom;
  static address _requested_open_region_top;
  static address _requested_closed_region_bottom;
  static address _requested_closed_region_top;

  static ArchiveHeapBitmapInfo _closed_oopmap_info;
  static ArchiveHeapBitmapInfo _open_oopmap_info;

  static GrowableArrayCHeap<NativePointerInfo, mtClassShared>* _native_pointers;
  static GrowableArrayCHeap<oop, mtClassShared>* _source_objs;

  typedef ResourceHashtable<oop, int,
      36137, // prime number
      AnyObj::C_HEAP,
      mtClassShared,
      HeapShared::oop_hash> BufferedObjToOutputOffsetTable;
  static BufferedObjToOutputOffsetTable* _buffered_obj_to_output_offset_table;

  typedef ResourceHashtable<int, oop,
      36137, // prime number
      AnyObj::C_HEAP,
      mtClassShared> OutputOffsetToOrigObjectTable;
  static OutputOffsetToOrigObjectTable* _output_offset_to_orig_obj_table;

  static int byte_size_of_buffered_obj(oop buffered_obj);
  static int cast_to_int_byte_size(size_t byte_size);

  static void allocate_output_array();
  static void copy_buffered_objs_to_output();
  static void copy_buffered_objs_to_output_by_region(bool copy_open_region);
  static int copy_one_buffered_obj_to_output(oop buffered_obj);
  static void fill_gc_region_gap(int required_byte_size);
  static int filler_array_byte_size(int length);
  static int filler_array_length(int fill_bytes);
  static void init_filler_array_at_output_top(int array_length, int fill_bytes);

  static void set_requested_address_for_regions(GrowableArray<MemRegion>* closed_regions,
                                                GrowableArray<MemRegion>* open_regions);
  static void relocate_embedded_pointers_in_output(GrowableArray<ArchiveHeapBitmapInfo>* closed_bitmaps,
                                                   GrowableArray<ArchiveHeapBitmapInfo>* open_bitmaps);
  static ArchiveHeapBitmapInfo compute_ptrmap(bool is_open);
  static ArchiveHeapBitmapInfo get_bitmap_info(ResourceBitMap* bitmap, bool is_open,  bool is_oopmap);
  static bool is_in_requested_regions(oop o);
  static oop requested_obj_from_output_offset(int offset);
  static oop buffered_obj_to_requested_obj(oop buffered_obj);
  static oop buffered_obj_to_output_obj(oop buffered_obj);

  static void store_in_output(oop* p, oop output_referent);
  static void store_in_output(narrowOop* p, oop output_referent);

  template <typename T> static T* requested_addr_to_output_addr(T* p);

public:
  static void init(TRAPS) NOT_CDS_JAVA_HEAP_RETURN;
  static bool is_object_too_large(size_t size);
  static void add_source_obj(oop src_obj);
  static HeapWord* allocate_buffer_for(oop orig_obj);
  static HeapWord* allocate_raw_buffer(size_t size);
  static bool is_in_buffer(oop o);
  static void finalize(GrowableArray<MemRegion>* closed_regions, GrowableArray<MemRegion>* open_regions,
                       GrowableArray<ArchiveHeapBitmapInfo>* closed_bitmaps,
                       GrowableArray<ArchiveHeapBitmapInfo>* open_bitmaps);
  static address heap_region_requested_bottom(int heap_region_idx);
  static oop heap_roots_requested_address();
  static address heap_roots_output_address();
  static oop requested_address_for_oop(oop orig_obj);

  static void mark_native_pointer(oop orig_obj, int offset);
  static oop output_addr_to_orig_oop(address output_addr);
  static address to_requested_address(address output_addr);
};
#endif // INCLUDE_CDS_JAVA_HEAP
#endif // SHARE_CDS_ARCHIVEHEAPWRITER_HPP
