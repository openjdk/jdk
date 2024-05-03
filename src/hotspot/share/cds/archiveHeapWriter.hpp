/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

class MemRegion;

class ArchiveHeapInfo {
  MemRegion _buffer_region;             // Contains the archived objects to be written into the CDS archive.
  CHeapBitMap _oopmap;
  CHeapBitMap _ptrmap;
  size_t _heap_roots_offset;            // Offset of the HeapShared::roots() object, from the bottom
                                        // of the archived heap objects, in bytes.

public:
  ArchiveHeapInfo() : _buffer_region(), _oopmap(128, mtClassShared), _ptrmap(128, mtClassShared) {}
  bool is_used() { return !_buffer_region.is_empty(); }

  MemRegion buffer_region() { return _buffer_region; }
  void set_buffer_region(MemRegion r) { _buffer_region = r; }

  char* buffer_start() { return (char*)_buffer_region.start(); }
  size_t buffer_byte_size() { return _buffer_region.byte_size();    }

  CHeapBitMap* oopmap() { return &_oopmap; }
  CHeapBitMap* ptrmap() { return &_ptrmap; }

  void set_heap_roots_offset(size_t n) { _heap_roots_offset = n; }
  size_t heap_roots_offset() const { return _heap_roots_offset; }
};

#if INCLUDE_CDS_JAVA_HEAP
class ArchiveHeapWriter : AllStatic {
  // ArchiveHeapWriter manipulates three types of addresses:
  //
  //     "source" vs "buffered" vs "requested"
  //
  // (Note: the design and convention is the same as for the archiving of Metaspace objects.
  //  See archiveBuilder.hpp.)
  //
  // - "source objects" are regular Java objects allocated during the execution
  //   of "java -Xshare:dump". They can be used as regular oops.
  //
  //   HeapShared::archive_objects() recursively searches for the oops that need to be
  //   stored into the CDS archive. These are entered into HeapShared::archived_object_cache().
  //
  // - "buffered objects" are copies of the "source objects", and are stored in into
  //   ArchiveHeapWriter::_buffer, which is a GrowableArray that sits outside of
  //   the valid heap range. Therefore we avoid using the addresses of these copies
  //   as oops. They are usually called "buffered_addr" in the code (of the type "address").
  //
  //   The buffered objects are stored contiguously, possibly with interleaving fillers
  //   to make sure no objects span across boundaries of MIN_GC_REGION_ALIGNMENT.
  //
  // - Each archived object has a "requested address" -- at run time, if the object
  //   can be mapped at this address, we can avoid relocation.
  //
  // The requested address is implemented differently depending on UseCompressedOops:
  //
  // UseCompressedOops == true:
  //   The archived objects are stored assuming that the runtime COOPS compression
  //   scheme is exactly the same as in dump time (or else a more expensive runtime relocation
  //   would be needed.)
  //
  //   At dump time, we assume that the runtime heap range is exactly the same as
  //   in dump time. The requested addresses of the archived objects are chosen such that
  //   they would occupy the top end of a G1 heap (TBD when dumping is supported by other
  //   collectors. See JDK-8298614).
  //
  // UseCompressedOops == false:
  //   At runtime, the heap range is usually picked (randomly) by the OS, so we will almost always
  //   need to perform relocation. Hence, the goal of the "requested address" is to ensure that
  //   the contents of the archived objects are deterministic. I.e., the oop fields of archived
  //   objects will always point to deterministic addresses.
  //
  //   For G1, the archived heap is written such that the lowest archived object is placed
  //   at NOCOOPS_REQUESTED_BASE. (TBD after JDK-8298614).
  // ----------------------------------------------------------------------

public:
  static const intptr_t NOCOOPS_REQUESTED_BASE = 0x10000000;

private:
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

  static GrowableArrayCHeap<u1, mtClassShared>* _buffer;

  // The number of bytes that have written into _buffer (may be smaller than _buffer->length()).
  static size_t _buffer_used;

  // The bottom of the copy of Heap::roots() inside this->_buffer.
  static size_t _heap_roots_offset;
  static size_t _heap_roots_word_size;

  // The address range of the requested location of the archived heap objects.
  static address _requested_bottom;
  static address _requested_top;

  static GrowableArrayCHeap<NativePointerInfo, mtClassShared>* _native_pointers;
  static GrowableArrayCHeap<oop, mtClassShared>* _source_objs;

  // We sort _source_objs_order to minimize the number of bits in ptrmap and oopmap.
  // See comments near the body of ArchiveHeapWriter::compare_objs_by_oop_fields().
  // The objects will be written in the order of:
  //_source_objs->at(_source_objs_order->at(0)._index)
  // source_objs->at(_source_objs_order->at(1)._index)
  // source_objs->at(_source_objs_order->at(2)._index)
  // ...
  struct HeapObjOrder {
    int _index;    // The location of this object in _source_objs
    int _rank;     // A lower rank means the object will be written at a lower location.
  };
  static GrowableArrayCHeap<HeapObjOrder, mtClassShared>* _source_objs_order;

  typedef ResizeableResourceHashtable<size_t, oop,
      AnyObj::C_HEAP,
      mtClassShared> BufferOffsetToSourceObjectTable;
  static BufferOffsetToSourceObjectTable* _buffer_offset_to_source_obj_table;

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

  static void copy_roots_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots);
  static void copy_source_objs_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots);
  static size_t copy_one_source_obj_to_buffer(oop src_obj);

  static void maybe_fill_gc_region_gap(size_t required_byte_size);
  static size_t filler_array_byte_size(int length);
  static int filler_array_length(size_t fill_bytes);
  static HeapWord* init_filler_array_at_buffer_top(int array_length, size_t fill_bytes);

  static void set_requested_address(ArchiveHeapInfo* info);
  static void relocate_embedded_oops(GrowableArrayCHeap<oop, mtClassShared>* roots, ArchiveHeapInfo* info);
  static void compute_ptrmap(ArchiveHeapInfo *info);
  static bool is_in_requested_range(oop o);
  static oop requested_obj_from_buffer_offset(size_t offset);

  static oop load_oop_from_buffer(oop* buffered_addr);
  static oop load_oop_from_buffer(narrowOop* buffered_addr);
  inline static void store_oop_in_buffer(oop* buffered_addr, oop requested_obj);
  inline static void store_oop_in_buffer(narrowOop* buffered_addr, oop requested_obj);

  template <typename T> static oop load_source_oop_from_buffer(T* buffered_addr);
  template <typename T> static void store_requested_oop_in_buffer(T* buffered_addr, oop request_oop);

  template <typename T> static T* requested_addr_to_buffered_addr(T* p);
  template <typename T> static void relocate_field_in_buffer(T* field_addr_in_buffer, CHeapBitMap* oopmap);
  template <typename T> static void mark_oop_pointer(T* buffered_addr, CHeapBitMap* oopmap);
  template <typename T> static void relocate_root_at(oop requested_roots, int index, CHeapBitMap* oopmap);

  static void update_header_for_requested_obj(oop requested_obj, oop src_obj, Klass* src_klass);

  static int compare_objs_by_oop_fields(HeapObjOrder* a, HeapObjOrder* b);
  static void sort_source_objs();

public:
  static void init() NOT_CDS_JAVA_HEAP_RETURN;
  static void add_source_obj(oop src_obj);
  static bool is_too_large_to_archive(size_t size);
  static bool is_too_large_to_archive(oop obj);
  static bool is_string_too_large_to_archive(oop string);
  static void write(GrowableArrayCHeap<oop, mtClassShared>*, ArchiveHeapInfo* heap_info);
  static address requested_address();  // requested address of the lowest achived heap object
  static oop heap_roots_requested_address(); // requested address of HeapShared::roots()
  static address buffered_heap_roots_addr() {
    return offset_to_buffered_address<address>(_heap_roots_offset);
  }
  static size_t heap_roots_word_size() {
    return _heap_roots_word_size;
  }
  static size_t get_filler_size_at(address buffered_addr);

  static void mark_native_pointer(oop src_obj, int offset);
  static bool is_marked_as_native_pointer(ArchiveHeapInfo* heap_info, oop src_obj, int field_offset);
  static oop source_obj_to_requested_obj(oop src_obj);
  static oop buffered_addr_to_source_obj(address buffered_addr);
  static address buffered_addr_to_requested_addr(address buffered_addr);

  // Archived heap object headers carry pre-computed narrow Klass ids calculated with the
  // following scheme:
  // 1) the encoding base must be the mapping start address.
  // 2) shift must be large enough to result in an encoding range that covers the runtime Klass range.
  //    That Klass range is defined by CDS archive size and runtime class space size. Luckily, the maximum
  //    size can be predicted: archive size is assumed to be <1G, class space size capped at 3G, and at
  //    runtime we put both regions adjacent to each other. Therefore, runtime Klass range size < 4G.
  //    Since nKlass itself is 32 bit, our encoding range len is 4G, and since we set the base directly
  //    at mapping start, these 4G are enough. Therefore, we don't need to shift at all (shift=0).
  static constexpr int precomputed_narrow_klass_shift = 0;

};
#endif // INCLUDE_CDS_JAVA_HEAP
#endif // SHARE_CDS_ARCHIVEHEAPWRITER_HPP
