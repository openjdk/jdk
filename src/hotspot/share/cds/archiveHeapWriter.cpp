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

#include "precompiled.hpp"
#include "cds/archiveHeapWriter.hpp"
#include "cds/filemap.hpp"
#include "cds/heapShared.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/oopFactory.hpp"
#include "memory/universe.hpp"
#include "oops/compressedOops.hpp"
#include "oops/oop.inline.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/typeArrayKlass.hpp"
#include "oops/typeArrayOop.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/growableArray.hpp"

#if INCLUDE_G1GC
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/heapRegion.hpp"
#endif

#if INCLUDE_CDS_JAVA_HEAP

// buffer
OopHandle ArchiveHeapWriter::_buffer;
int ArchiveHeapWriter::_buffer_top;

// output
GrowableArrayCHeap<u1, mtClassShared>* ArchiveHeapWriter::_output;
// The folling ints are indices into ArchiveHeapWriter::_output
int ArchiveHeapWriter::_output_top;
int ArchiveHeapWriter::_open_bottom;
int ArchiveHeapWriter::_open_top;
int ArchiveHeapWriter::_closed_bottom;
int ArchiveHeapWriter::_closed_top;
int ArchiveHeapWriter::_heap_roots_bottom;

address ArchiveHeapWriter::_requested_open_region_bottom;
address ArchiveHeapWriter::_requested_open_region_top;
address ArchiveHeapWriter::_requested_closed_region_bottom;
address ArchiveHeapWriter::_requested_closed_region_top;

GrowableArrayCHeap<ArchiveHeapWriter::NativePointerInfo, mtClassShared>* ArchiveHeapWriter::_native_pointers;
GrowableArrayCHeap<oop, mtClassShared>* ArchiveHeapWriter::_source_objs;

ArchiveHeapWriter::BufferedObjToOutputOffsetTable*
  ArchiveHeapWriter::_buffered_obj_to_output_offset_table = NULL;

ArchiveHeapWriter::OutputOffsetToOrigObjectTable*
  ArchiveHeapWriter::_output_offset_to_orig_obj_table = NULL;

void ArchiveHeapWriter::init(TRAPS) {
  Universe::heap()->collect(GCCause::_java_lang_system_gc);
  size_t heap_used_bytes;
  {
    MonitorLocker ml(Heap_lock);
    heap_used_bytes = Universe::heap()->used();
  }

  size_t buffer_size_bytes = align_up(heap_used_bytes * 2 + 1, ObjectAlignmentInBytes);
  typeArrayOop buffer_oop = oopFactory::new_byteArray(buffer_size_bytes, CHECK);

  log_info(cds, heap)("Heap used = " SIZE_FORMAT, heap_used_bytes);
  log_info(cds, heap)("Max buffer size = " SIZE_FORMAT, buffer_size_bytes);
  log_info(cds, heap)("Max buffer oop = " INTPTR_FORMAT, p2i(buffer_oop));

  _buffer = OopHandle(Universe::vm_global(), buffer_oop);
  for (_buffer_top = 0; !is_aligned(buffer_oop->byte_at_addr(_buffer_top), ObjectAlignmentInBytes); _buffer_top++) {}

  _buffered_obj_to_output_offset_table = new BufferedObjToOutputOffsetTable();
  _output_offset_to_orig_obj_table = new OutputOffsetToOrigObjectTable();

  _requested_open_region_bottom = NULL;
  _requested_open_region_top = NULL;
  _requested_closed_region_bottom = NULL;
  _requested_closed_region_top = NULL;

  _native_pointers = new GrowableArrayCHeap<NativePointerInfo, mtClassShared>(2048);
  _source_objs = new GrowableArrayCHeap<oop, mtClassShared>(10000);
}

void ArchiveHeapWriter::add_source_obj(oop src_obj) {
  _source_objs->append(src_obj);
}

bool ArchiveHeapWriter::is_object_too_large(size_t size) {
  assert(size > 0, "no zero-size object");
  assert(size * HeapWordSize > size, "no overflow");
  static_assert(MIN_GC_REGION_ALIGNMENT > 0, "must be positive");

  size_t byte_size = size * HeapWordSize;
  if (byte_size > size_t(MIN_GC_REGION_ALIGNMENT)) {
    return true;
  } else {
    return false;
  }
}

int ArchiveHeapWriter::cast_to_int_byte_size(size_t byte_size) {
  assert(byte_size <= size_t(MIN_GC_REGION_ALIGNMENT), "must be");
  static_assert(MIN_GC_REGION_ALIGNMENT < max_jint, "must be");
  return (int)(byte_size);
}

int ArchiveHeapWriter::byte_size_of_buffered_obj(oop buffered_obj) {
  assert(!is_object_too_large(buffered_obj->size()), "sanity");
  int sz = cast_to_int_byte_size(buffered_obj->size() * HeapWordSize);
  return align_up(sz, ObjectAlignmentInBytes);
}

HeapWord* ArchiveHeapWriter::allocate_buffer_for(oop orig_obj) {
  size_t size = orig_obj->size();
  return allocate_raw_buffer(size);
}

HeapWord* ArchiveHeapWriter::allocate_raw_buffer(size_t size) {
  assert(size > 0, "no zero-size object");
  assert(size * HeapWordSize > size, "no overflow");

  static_assert(MIN_GC_REGION_ALIGNMENT < max_jint, "sanity");
  size_t byte_size = align_up(size * HeapWordSize, ObjectAlignmentInBytes);
  assert(byte_size < MIN_GC_REGION_ALIGNMENT, "should have been checked");
  assert(byte_size < max_jint, "sanity");

  typeArrayOop buffer_oop = (typeArrayOop)_buffer.resolve();
  int buffer_size = buffer_oop->length();

  int new_top = _buffer_top + (int)(byte_size);
  assert(new_top > _buffer_top, "no wrap around; no zero-size object");
  assert(new_top <= buffer_size, "We should have reserved enough buffer: newtop = %d, buffer_size = %d", new_top, buffer_size);

  jbyte* base =  buffer_oop->byte_at_addr(0);
  assert(is_aligned(base, HeapWordSize), "must be");

  jbyte* allocated = base + _buffer_top;
  _buffer_top = new_top;

  assert(is_aligned(allocated, ObjectAlignmentInBytes), "sanity");
  return (HeapWord*)allocated;
}

bool ArchiveHeapWriter::is_in_buffer(oop o) {
  typeArrayOop buffer_oop = (typeArrayOop)_buffer.resolve();
  jbyte* base =  buffer_oop->byte_at_addr(0);
  assert(is_aligned(base, HeapWordSize), "must be");

  jbyte* top = base + _buffer_top;

  return cast_to_oop(base) <= o && o < cast_to_oop(top);
}

bool ArchiveHeapWriter::is_in_requested_regions(oop o) {
  assert(_requested_open_region_bottom != NULL, "do not call before this is initialized");
  assert(_requested_closed_region_bottom != NULL, "do not call before this is initialized");

  address a = cast_from_oop<address>(o);
  return (_requested_open_region_bottom <= a && a < _requested_open_region_top) ||
         (_requested_closed_region_bottom <= a && a < _requested_closed_region_top);
}

oop ArchiveHeapWriter::requested_obj_from_output_offset(int offset) {
  oop req_obj = cast_to_oop(_requested_open_region_bottom + offset);
  assert(is_in_requested_regions(req_obj), "must be");
  return req_obj;
}

// For the time being, always support two regions (to be strictly compatible with existing G1
// mapping code. We should eventually use a single region.
void ArchiveHeapWriter::finalize(GrowableArray<MemRegion>* closed_regions, GrowableArray<MemRegion>* open_regions,
                                 GrowableArray<ArchiveHeapBitmapInfo>* closed_bitmaps,
                                 GrowableArray<ArchiveHeapBitmapInfo>* open_bitmaps) {
  copy_buffered_objs_to_output();
  set_requested_address_for_regions(closed_regions, open_regions);
  relocate_embedded_pointers_in_output(closed_bitmaps, open_bitmaps);
}

oop ArchiveHeapWriter::heap_roots_requested_address() {
  return cast_to_oop(_requested_open_region_bottom + _heap_roots_bottom);
}

address ArchiveHeapWriter::heap_roots_output_address() {
  return _output->adr_at(0) + _heap_roots_bottom;
}

address ArchiveHeapWriter::heap_region_requested_bottom(int heap_region_idx) {
  assert(_output != NULL, "must be initialized");
  switch (heap_region_idx) {
  case MetaspaceShared::first_closed_heap_region:
    return _requested_closed_region_bottom;
  case MetaspaceShared::first_open_heap_region:
    return _requested_open_region_bottom;
  default:
    ShouldNotReachHere();
    return NULL;
  }
}

void ArchiveHeapWriter::allocate_output_array() {
  int initial_buffer_size = _buffer_top;
  DEBUG_ONLY(initial_buffer_size = MAX2(10000, initial_buffer_size / 10)); // test for expansion logic
  _output = new GrowableArrayCHeap<u1, mtClassShared>(initial_buffer_size);
  _open_bottom = _output_top = 0;
}

void ArchiveHeapWriter::copy_buffered_objs_to_output() {
  allocate_output_array();
  for (int i = 0; i < 2; i ++) {
    // We copy the objects for the open region first, so that the end of the closed region
    // aligns with the end of the heap.
    //
    // TODO: ascii art
    bool copy_open_region = (i == 0) ? true : false;
    copy_buffered_objs_to_output_by_region(copy_open_region);
    if (i == 0) {
      _heap_roots_bottom = copy_one_buffered_obj_to_output(HeapShared::roots()); // this is not in HeapShared::archived_object_cache()
      bool is_new = _buffered_obj_to_output_offset_table->put(HeapShared::roots(), _heap_roots_bottom);
      assert(is_new, "sanity"); // FIXME -- move to a function

      _open_top = _output_top;
      _output_top = _closed_bottom = align_up(_output_top, HeapRegion::GrainBytes);
    }
  }
  _closed_top = _output_top;

  log_info(cds, heap)("Size of open region   = %d bytes", _open_top   - _open_bottom);
  log_info(cds, heap)("Size of closed region = %d bytes", _closed_top - _closed_bottom);
}

void ArchiveHeapWriter::copy_buffered_objs_to_output_by_region(bool copy_open_region) {
  for (int i = 0; i < _source_objs->length(); i++) {
    oop orig_obj = _source_objs->at(i);
    HeapShared::CachedOopInfo* info = HeapShared::archived_object_cache()->get(orig_obj);
    assert(info != NULL, "must be");
    if (info->in_open_region() == copy_open_region) {
      // For region-based collectors such as G1, we need to make sure that we don't have
      // an object that can possible span across two regions.
      int output_offset = copy_one_buffered_obj_to_output(info->buffered_obj());
      info->set_output_offset(output_offset);

      _output_offset_to_orig_obj_table->put(output_offset, orig_obj);

      bool is_new = _buffered_obj_to_output_offset_table->put(info->buffered_obj(), output_offset);
      assert(is_new, "sanity");
    }
  }
}


int ArchiveHeapWriter::filler_array_byte_size(int length) {
  int byte_size = int(objArrayOopDesc::object_size(length) * HeapWordSize);
  return align_up(byte_size, ObjectAlignmentInBytes);
}

int ArchiveHeapWriter::filler_array_length(int fill_bytes) {
  assert(is_aligned(fill_bytes, ObjectAlignmentInBytes), "must be");
  TypeArrayKlass* bytearray_klass = TypeArrayKlass::cast(Universe::byteArrayKlassObj());
  int elemSize = (UseCompressedOops ? sizeof(narrowOop) : sizeof(oop));

  for (int length = fill_bytes / elemSize; length >= 0; length --) {
    int array_byte_size = filler_array_byte_size(length);
    if (array_byte_size == fill_bytes) {
      return length;
    }
  }

  ShouldNotReachHere();
  return -1;
}

void ArchiveHeapWriter::init_filler_array_at_output_top(int array_length, int fill_bytes) {
  assert(UseCompressedClassPointers, "Archived heap only supported for compressed klasses");
  Klass* k = Universe::objectArrayKlassObj(); // already relocated to point to archived klass
  HeapWord* mem = (HeapWord*)_output->adr_at(_output_top);
  memset(mem, 0, fill_bytes);
  oopDesc::set_mark(mem, markWord::prototype());
  narrowKlass nk = ArchiveBuilder::current()->get_requested_narrow_klass(k); // TODO: Comment:
  cast_to_oop(mem)->set_narrow_klass(nk);
  arrayOopDesc::set_length(mem, array_length);
}

void ArchiveHeapWriter::fill_gc_region_gap(int required_byte_size) {
  int min_filler_byte_size = filler_array_byte_size(0);
  int new_top = _output_top + required_byte_size + min_filler_byte_size;

  const int cur_min_region_bottom = align_down(_output_top, MIN_GC_REGION_ALIGNMENT);
  const int next_min_region_bottom = align_down(new_top, MIN_GC_REGION_ALIGNMENT);

  if (cur_min_region_bottom != next_min_region_bottom) {
    // Make sure that no objects span across MIN_GC_REGION_ALIGNMENT. This way
    // we can map the region in any region-based collector.
    assert(next_min_region_bottom > cur_min_region_bottom, "must be");
    assert(next_min_region_bottom - cur_min_region_bottom == MIN_GC_REGION_ALIGNMENT, "no buffered object can be larger than %d bytes",
           MIN_GC_REGION_ALIGNMENT);

    const int filler_end = next_min_region_bottom;
    const int fill_bytes = filler_end - _output_top;
    assert(fill_bytes > 0, "must be");
    while (_output->length() < filler_end) {
      _output->append(0); // TODO: make into a function
    }

    int array_length = filler_array_length(fill_bytes);
    log_info(cds, heap)("Inserting filler obj array of %d elements (%d bytes total) @ output offset %d",
                        array_length, fill_bytes, _output_top);
    init_filler_array_at_output_top(array_length, fill_bytes);

    _output_top = filler_end;
  }
}

int ArchiveHeapWriter::copy_one_buffered_obj_to_output(oop buffered_obj) {
  assert(is_in_buffer(buffered_obj), "sanity");
  int byte_size = byte_size_of_buffered_obj(buffered_obj);
  assert(byte_size > 0, "no zero-size objects");

  fill_gc_region_gap(byte_size);

  int new_top = _output_top + byte_size;
  assert(new_top > _output_top, "no wrap around");

  int cur_min_region_bottom = align_down(_output_top, MIN_GC_REGION_ALIGNMENT);
  int next_min_region_bottom = align_down(new_top, MIN_GC_REGION_ALIGNMENT);
  assert(cur_min_region_bottom == next_min_region_bottom, "no object should cross minimal GC region boundaries");

  while (_output->length() < new_top) {
    _output->append(0); // FIXME -- grow in blocks!
  }

  u1* from = cast_from_oop<u1*>(buffered_obj);
  u1* to = _output->adr_at(_output_top);
  assert(is_aligned(_output_top, ObjectAlignmentInBytes), "sanity");
  assert(is_aligned(byte_size, ObjectAlignmentInBytes), "sanity");
  memcpy(to, from, byte_size);

  int output_offset = _output_top;
  _output_top = new_top;

  return output_offset;
}

void ArchiveHeapWriter::set_requested_address_for_regions(GrowableArray<MemRegion>* closed_regions,
                                                          GrowableArray<MemRegion>* open_regions) {
  assert(closed_regions->length() == 0, "must be");
  assert(open_regions->length() == 0, "must be");

  assert(UseG1GC, "must be");
  address heap_end = (address)G1CollectedHeap::heap()->reserved().end();
  log_info(cds, heap)("Heap end = %p", heap_end);

  int closed_region_byte_size = _closed_top - _closed_bottom;
  int open_region_byte_size = _open_top - _open_bottom;
  assert(closed_region_byte_size > 0, "must archived at least one object for closed region!");
  assert(open_region_byte_size > 0, "must archived at least one object for open region!");

  // The following two asserts are ensured by copy_buffered_objs_to_output_by_region().
  assert(is_aligned(_closed_bottom, HeapRegion::GrainBytes), "sanity");
  assert(is_aligned(_open_bottom, HeapRegion::GrainBytes), "sanity");

  _requested_closed_region_bottom = align_down(heap_end - closed_region_byte_size, HeapRegion::GrainBytes);
  _requested_open_region_bottom = _requested_closed_region_bottom - (_closed_bottom - _open_bottom);

  assert(is_aligned(_requested_closed_region_bottom, HeapRegion::GrainBytes), "sanity");
  assert(is_aligned(_requested_open_region_bottom, HeapRegion::GrainBytes), "sanity");

  _requested_open_region_top = _requested_open_region_bottom + (_open_top - _open_bottom);
  _requested_closed_region_top = _requested_closed_region_bottom + (_closed_top - _closed_bottom);

  assert(_requested_open_region_top <= _requested_closed_region_bottom, "no overlap");

  // Here are the location of the output buffers
  address output_base = _output->adr_at(0);
  closed_regions->append(MemRegion((HeapWord*)(output_base + _closed_bottom), (HeapWord*)(output_base + _closed_top)));
  open_regions->append(  MemRegion((HeapWord*)(output_base + _open_bottom),   (HeapWord*)(output_base + _open_top)));
}

oop ArchiveHeapWriter::buffered_obj_to_requested_obj(oop buffered_obj) {
  assert(is_in_buffer(buffered_obj), "Hah!");
  int* p = _buffered_obj_to_output_offset_table->get(buffered_obj);
  assert(p != NULL, "must have copied " INTPTR_FORMAT " to output", p2i(buffered_obj));
  int output_offset = *p;
  oop requested_obj = requested_obj_from_output_offset(output_offset);

  return requested_obj;
}

oop ArchiveHeapWriter::buffered_obj_to_output_obj(oop buffered_obj) {
  assert(is_in_buffer(buffered_obj), "Hah!");
  int* p = _buffered_obj_to_output_offset_table->get(buffered_obj);
  assert(p != NULL, "must have copied " INTPTR_FORMAT " to output", p2i(buffered_obj));
  int output_offset = *p;
  oop output_obj = cast_to_oop(_output->adr_at(output_offset));

  return output_obj;
}

template <typename T> T* ArchiveHeapWriter::requested_addr_to_output_addr(T* p) {
  assert(is_in_requested_regions(cast_to_oop(p)), "must be");

  address addr = address(p);
  assert(addr >= _requested_open_region_bottom, "must be");
  size_t offset = addr - _requested_open_region_bottom;
  address output_addr = address(_output->adr_at(offset));
  return (T*)(output_addr);
}
oop ArchiveHeapWriter::requested_address_for_oop(oop orig_obj) {
  assert(DumpSharedSpaces, "dump-time only");
  HeapShared::CachedOopInfo* p = HeapShared::archived_object_cache()->get(orig_obj);
  if (p != NULL) {
    return requested_obj_from_output_offset(p->output_offset());
  } else {
    return NULL;
  }
}

void ArchiveHeapWriter::store_in_output(oop* request_p, oop request_referent) {
  oop* output_addr = requested_addr_to_output_addr(request_p);
  // Make heap content deterministic. See comments inside HeapShared::to_requested_address.
  *output_addr = HeapShared::to_requested_address(request_referent);
}

void ArchiveHeapWriter::store_in_output(narrowOop* request_p, oop request_referent) {
  // Note: HeapShared::to_requested_address() is not necessary because
  // the heap always starts at a deterministic address with UseCompressedOops==true.
  narrowOop val = CompressedOops::encode_not_null(request_referent);
  narrowOop* output_addr = requested_addr_to_output_addr(request_p);
  *output_addr = val;
}

class ArchiveHeapWriter::EmbeddedOopRelocator: public BasicOopIterateClosure {
  oop _buffered_obj;
  oop _request_obj;
  ResourceBitMap* _oopmap;
  address _requested_region_bottom;
public:
  EmbeddedOopRelocator(oop buffered_obj, oop request_obj, ResourceBitMap* oopmap, address requested_region_bottom) :
    _buffered_obj(buffered_obj), _request_obj(request_obj),
    _oopmap(oopmap), _requested_region_bottom(requested_region_bottom) {
    assert(UseCompressedClassPointers, "Archived heap only supported for compressed klasses");
    narrowKlass nk = ArchiveBuilder::current()->get_requested_narrow_klass(buffered_obj->klass());
    buffered_obj_to_output_obj(buffered_obj)->set_narrow_klass(nk);
  }

  void do_oop(narrowOop *p) { EmbeddedOopRelocator::do_oop_work(p); }
  void do_oop(      oop *p) { EmbeddedOopRelocator::do_oop_work(p); }

private:
  template <class T> void do_oop_work(T *p) {
    oop buffered_referent = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(buffered_referent)) {
      oop request_referent = buffered_obj_to_requested_obj(buffered_referent);
      size_t field_offset = pointer_delta(p, _buffered_obj, sizeof(char));
      T* request_p = (T*)(cast_from_oop<address>(_request_obj) + field_offset);
      ArchiveHeapWriter::store_in_output(request_p, request_referent);

      // Mark the pointer in the oopmap
      T* region_bottom = (T*)_requested_region_bottom;
      assert(request_p >= region_bottom, "must be");
      BitMap::idx_t idx = request_p - region_bottom;
      assert(idx < _oopmap->size(), "overflow");
      _oopmap->set_bit(idx);
    }
  }
};

void ArchiveHeapWriter::relocate_embedded_pointers_in_output(GrowableArray<ArchiveHeapBitmapInfo>* closed_bitmaps,
                                                             GrowableArray<ArchiveHeapBitmapInfo>* open_bitmaps) {
  size_t oopmap_unit = (UseCompressedOops ? sizeof(narrowOop) : sizeof(oop));
  int closed_region_byte_size = _closed_top - _closed_bottom;
  int open_region_byte_size   = _open_top   - _open_bottom;
  ResourceBitMap closed_oopmap(closed_region_byte_size / oopmap_unit);
  ResourceBitMap open_oopmap  (open_region_byte_size   / oopmap_unit);

  auto iterator = [&] (oop orig_obj, HeapShared::CachedOopInfo& info) {
    ResourceBitMap* oopmap;
    address requested_region_bottom;
    if (info.in_open_region()) {
      oopmap = &open_oopmap;
      requested_region_bottom = _requested_open_region_bottom;
    } else {
      oopmap = &closed_oopmap;
      requested_region_bottom = _requested_closed_region_bottom;
    }

    oop buffered_obj = info.buffered_obj();
    oop requested_obj = requested_obj_from_output_offset(info.output_offset());
    EmbeddedOopRelocator relocator(buffered_obj, requested_obj, oopmap, requested_region_bottom);

    buffered_obj->oop_iterate(&relocator);
  };
  HeapShared::archived_object_cache()->iterate_all(iterator);

  oop buffered_roots = HeapShared::roots();
  oop requested_roots = requested_obj_from_output_offset(_heap_roots_bottom);
  EmbeddedOopRelocator relocate_roots(buffered_roots, requested_roots,
                                      &open_oopmap, _requested_open_region_bottom);
  buffered_roots->oop_iterate(&relocate_roots);

  closed_bitmaps->append(get_bitmap_info(&closed_oopmap, /*is_open=*/false, /*is_oopmap=*/true));
  open_bitmaps  ->append(get_bitmap_info(&open_oopmap,   /*is_open=*/false, /*is_oopmap=*/true));

  closed_bitmaps->append(compute_ptrmap(/*is_open=*/false));
  open_bitmaps  ->append(compute_ptrmap(/*is_open=*/true));
}

ArchiveHeapBitmapInfo ArchiveHeapWriter::get_bitmap_info(ResourceBitMap* bitmap, bool is_open,  bool is_oopmap) {
  size_t size_in_bits = bitmap->size();
  size_t size_in_bytes;
  uintptr_t* buffer;

  if (size_in_bits > 0) {
    size_in_bytes = bitmap->size_in_bytes();
    buffer = (uintptr_t*)NEW_C_HEAP_ARRAY(char, size_in_bytes, mtInternal);
    bitmap->write_to(buffer, size_in_bytes);
  } else {
    size_in_bytes = 0;
    buffer = NULL;
  }

  log_info(cds, heap)("%s @ " INTPTR_FORMAT " (" SIZE_FORMAT_W(6) " bytes) for %s heap region",
                      is_oopmap ? "Oopmap" : "Ptrmap",
                      p2i(buffer), size_in_bytes,
                      is_open? "open" : "closed");

  ArchiveHeapBitmapInfo info;
  info._map = (address)buffer;
  info._size_in_bits = size_in_bits;
  info._size_in_bytes = size_in_bytes;

  return info;
}

void ArchiveHeapWriter::mark_native_pointer(oop orig_obj, int field_offset) {
  Metadata* ptr = orig_obj->metadata_field_acquire(field_offset);
  if (ptr != NULL) {
    NativePointerInfo info;
    info._orig_obj = orig_obj;
    info._field_offset = field_offset;
    _native_pointers->append(info);
  }
}

oop ArchiveHeapWriter::output_addr_to_orig_oop(address output_addr) {
  address output_base = _output->adr_at(0);
  int output_offset = output_addr - output_base;
  oop* p = _output_offset_to_orig_obj_table->get(output_offset);
  if (p != NULL) {
    return *p;
  } else {
    return NULL;
  }
}

address ArchiveHeapWriter::to_requested_address(address output_addr) {
  address output_base = _output->adr_at(0);
  int output_offset = output_addr - output_base;
  return _requested_open_region_bottom + output_offset;
}

ArchiveHeapBitmapInfo ArchiveHeapWriter::compute_ptrmap(bool is_open) {
  int num_non_null_ptrs = 0;
  Metadata** bottom = (Metadata**) (is_open ? _requested_open_region_bottom: _requested_closed_region_bottom);
  Metadata** top = (Metadata**) (is_open ? _requested_open_region_top: _requested_closed_region_top); // exclusive
  ResourceBitMap ptrmap(top - bottom);

  for (int i = 0; i < _native_pointers->length(); i++) {
    NativePointerInfo info = _native_pointers->at(i);
    oop orig_obj = info._orig_obj;
    int field_offset = info._field_offset;
    HeapShared::CachedOopInfo* p = HeapShared::archived_object_cache()->get(orig_obj);
    if (p->in_open_region() == is_open) {
      // requested_field_addr = the address of this field in the requested space
      oop requested_obj = requested_obj_from_output_offset(p->output_offset());
      Metadata** requested_field_addr = (Metadata**)(cast_from_oop<address>(requested_obj) + field_offset);
      assert(bottom <= requested_field_addr && requested_field_addr < top, "range check");

      // Mark this field in the bitmap
      BitMap::idx_t idx = requested_field_addr - bottom;
      ptrmap.set_bit(idx);
      num_non_null_ptrs ++;

      // Set the native pointer to the requested address of the metadata (at runtime, the metadata will have
      // this address if the RO/RW regions are mapped at the default location).

      Metadata** output_field_addr = requested_addr_to_output_addr(requested_field_addr);
      Metadata* native_ptr = *output_field_addr;
      assert(native_ptr != NULL, "sanity");

      address buffered_native_ptr = ArchiveBuilder::current()->get_buffered_addr((address)native_ptr);
      address requested_native_ptr = ArchiveBuilder::current()->to_requested(buffered_native_ptr);
      *output_field_addr = (Metadata*)requested_native_ptr;
    }
  }

  log_info(cds, heap)("calculate_ptrmap: marked %d non-null native pointers for %s heap region",
                      num_non_null_ptrs, is_open ? "open" : "closed");

  if (num_non_null_ptrs == 0) {
    ResourceBitMap empty;
    return get_bitmap_info(&empty, is_open, /*is_oopmap=*/ false);
  } else {
    return get_bitmap_info(&ptrmap, is_open, /*is_oopmap=*/ false);
  }
}

#endif // INCLUDE_CDS_JAVA_HEAP
