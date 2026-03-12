/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_AOTMAPPEDHEAPWRITER_HPP
#define SHARE_CDS_AOTMAPPEDHEAPWRITER_HPP

#include "cds/aotMapLogger.hpp"
#include "cds/heapShared.hpp"
#include "memory/allocation.hpp"
#include "memory/allStatic.hpp"
#include "oops/compressedOops.hpp"
#include "oops/oopHandle.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/hashTable.hpp"
#include "utilities/macros.hpp"

class MemRegion;

#if INCLUDE_CDS_JAVA_HEAP
class DumpedInternedStrings :
  public ResizeableHashTable<oop, bool,
                           AnyObj::C_HEAP,
                           mtClassShared,
                           HeapShared::string_oop_hash>
{
public:
  DumpedInternedStrings(unsigned size, unsigned max_size) :
    ResizeableHashTable<oop, bool,
                                AnyObj::C_HEAP,
                                mtClassShared,
                                HeapShared::string_oop_hash>(size, max_size) {}
};

class AOTMappedHeapWriter : AllStatic {
  friend class HeapShared;
  friend class AOTMappedHeapLoader;
  // AOTMappedHeapWriter manipulates three types of addresses:
  //
  //     "source" vs "buffered" vs "requested"
  //
  // (Note: the design and convention is the same as for the archiving of Metaspace objects.
  //  See archiveBuilder.hpp.)
  //
  // - "source objects" are regular Java objects allocated during the execution
  //   of "java -Xshare:dump". They can be used as regular oops.
  //
  //   Between HeapShared::start_scanning_for_oops() and HeapShared::end_scanning_for_oops(),
  //   we recursively search for the oops that need to be stored into the CDS archive.
  //   These are entered into HeapShared::archived_object_cache().
  //
  // - "buffered objects" are copies of the "source objects", and are stored in into
  //   AOTMappedHeapWriter::_buffer, which is a GrowableArray that sits outside of
  //   the valid heap range. Therefore we avoid using the addresses of these copies
  //   as oops. They are usually called "buffered_addr" in the code (of the type "address").
  //
  //   The buffered objects are stored contiguously, possibly with interleaving fillers
  //   to make sure no objects span across boundaries of MIN_GC_REGION_ALIGNMENT.
  //
  // - Each archived object has a "requested address" -- at run time, if the object
  //   can be mapped at this address, we can avoid relocation.
  //
  // The requested address of an archived object is essentially its buffered_addr + delta,
  // where delta is (_requested_bottom - buffer_bottom());
  //
  // The requested addresses of all archived objects are within [_requested_bottom, _requested_top).
  // See AOTMappedHeapWriter::set_requested_address_range() for more info.
  // ----------------------------------------------------------------------

public:
  static const intptr_t NOCOOPS_REQUESTED_BASE = 0x10000000;

  // The minimum region size of all collectors that are supported by CDS.
  // G1 heap region size can never be smaller than 1M.
  // Shenandoah heap region size can never be smaller than 256K.
  static constexpr int MIN_GC_REGION_ALIGNMENT = 256 * K;

  // The heap contents are required to be deterministic when dumping "old" CDS archives, in order
  // to support reproducible lib/server/classes*.jsa when building the JDK.
  static bool is_writing_deterministic_heap() { return _is_writing_deterministic_heap; }

  // The oop encoding used by the archived heap objects.
  static CompressedOops::Mode narrow_oop_mode();
  static address narrow_oop_base();
  static int narrow_oop_shift();

  static const int INITIAL_TABLE_SIZE = 15889; // prime number
  static const int MAX_TABLE_SIZE     = 1000000;

private:
  class EmbeddedOopRelocator;
  struct NativePointerInfo {
    oop _src_obj;
    int _field_offset;
  };

  static bool _is_writing_deterministic_heap;
  static GrowableArrayCHeap<u1, mtClassShared>* _buffer;

  // The number of bytes that have written into _buffer (may be smaller than _buffer->length()).
  static size_t _buffer_used;

  // The heap root segments information.
  static HeapRootSegments _heap_root_segments;

  // The address range of the requested location of the archived heap objects.
  static address _requested_bottom; // The requested address of the lowest archived heap object
  static address _requested_top;    // The exclusive end of the highest archived heap object

  static GrowableArrayCHeap<NativePointerInfo, mtClassShared>* _native_pointers;
  static GrowableArrayCHeap<oop, mtClassShared>* _source_objs;
  static DumpedInternedStrings *_dumped_interned_strings;

  // We sort _source_objs_order to minimize the number of bits in ptrmap and oopmap.
  // See comments near the body of AOTMappedHeapWriter::compare_objs_by_oop_fields().
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

  typedef ResizeableHashTable<size_t, OopHandle,
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

  static void root_segment_at_put(objArrayOop segment, int index, oop root);
  static objArrayOop allocate_root_segment(size_t offset, int element_count);
  static void copy_roots_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots);
  static void copy_source_objs_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots);
  static size_t copy_one_source_obj_to_buffer(oop src_obj);

  static void maybe_fill_gc_region_gap(size_t required_byte_size);
  static size_t filler_array_byte_size(int length);
  static int filler_array_length(size_t fill_bytes);
  static HeapWord* init_filler_array_at_buffer_top(int array_length, size_t fill_bytes);

  static void set_requested_address_range(AOTMappedHeapInfo* info);
  static void mark_native_pointers(oop orig_obj);
  static void relocate_embedded_oops(GrowableArrayCHeap<oop, mtClassShared>* roots, AOTMappedHeapInfo* info);
  static void compute_ptrmap(AOTMappedHeapInfo *info);
  static bool is_in_requested_range(oop o);
  static oop requested_obj_from_buffer_offset(size_t offset);

  static oop load_oop_from_buffer(oop* buffered_addr);
  static oop load_oop_from_buffer(narrowOop* buffered_addr);
  inline static void store_oop_in_buffer(oop* buffered_addr, oop requested_obj);
  inline static void store_oop_in_buffer(narrowOop* buffered_addr, oop requested_obj);

  template <typename T> static oop load_source_oop_from_buffer(T* buffered_addr);
  template <typename T> static void store_requested_oop_in_buffer(T* buffered_addr, oop request_oop);

  template <typename T> static T* requested_addr_to_buffered_addr(T* p);
  template <typename T> static void relocate_field_in_buffer(T* field_addr_in_buffer, oop source_referent, CHeapBitMap* oopmap);
  template <typename T> static void mark_oop_pointer(T* buffered_addr, CHeapBitMap* oopmap);

  static void update_header_for_requested_obj(oop requested_obj, oop src_obj, Klass* src_klass);

  static int compare_objs_by_oop_fields(HeapObjOrder* a, HeapObjOrder* b);
  static void sort_source_objs();

public:
  static void init() NOT_CDS_JAVA_HEAP_RETURN;
  static void delete_tables_with_raw_oops();
  static void add_source_obj(oop src_obj);
  static bool is_too_large_to_archive(size_t size);
  static bool is_too_large_to_archive(oop obj);
  static bool is_string_too_large_to_archive(oop string);
  static bool is_dumped_interned_string(oop o);
  static void add_to_dumped_interned_strings(oop string);
  static void write(GrowableArrayCHeap<oop, mtClassShared>*, AOTMappedHeapInfo* heap_info);
  static address requested_address();  // requested address of the lowest achived heap object
  static size_t get_filler_size_at(address buffered_addr);

  static void mark_native_pointer(oop src_obj, int offset);
  static oop source_obj_to_requested_obj(oop src_obj);
  static oop buffered_addr_to_source_obj(address buffered_addr);
  static address buffered_addr_to_requested_addr(address buffered_addr);
  static Klass* real_klass_of_buffered_oop(address buffered_addr);
  static size_t size_of_buffered_oop(address buffered_addr);

  static AOTMapLogger::OopDataIterator* oop_iterator(AOTMappedHeapInfo* heap_info);
};
#endif // INCLUDE_CDS_JAVA_HEAP
#endif // SHARE_CDS_AOTMAPPEDHEAPWRITER_HPP
