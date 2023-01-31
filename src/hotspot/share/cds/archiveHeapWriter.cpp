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
#include "oops/objArrayOop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/typeArrayKlass.hpp"
#include "oops/typeArrayOop.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/bitMap.inline.hpp"

#if INCLUDE_G1GC
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/heapRegion.hpp"
#endif

#if INCLUDE_CDS_JAVA_HEAP


GrowableArrayCHeap<u1, mtClassShared>* ArchiveHeapWriter::_buffer;

// The following are offsets from buffer_bottom()
size_t ArchiveHeapWriter::_buffer_top;
size_t ArchiveHeapWriter::_open_bottom;
size_t ArchiveHeapWriter::_open_top;
size_t ArchiveHeapWriter::_closed_bottom;
size_t ArchiveHeapWriter::_closed_top;
size_t ArchiveHeapWriter::_heap_roots_bottom;

size_t ArchiveHeapWriter::_heap_roots_word_size;

address ArchiveHeapWriter::_requested_open_region_bottom;
address ArchiveHeapWriter::_requested_open_region_top;
address ArchiveHeapWriter::_requested_closed_region_bottom;
address ArchiveHeapWriter::_requested_closed_region_top;

ResourceBitMap* ArchiveHeapWriter::_closed_oopmap;
ResourceBitMap* ArchiveHeapWriter::_open_oopmap;

GrowableArrayCHeap<ArchiveHeapWriter::NativePointerInfo, mtClassShared>* ArchiveHeapWriter::_native_pointers;
GrowableArrayCHeap<oop, mtClassShared>* ArchiveHeapWriter::_source_objs;

ArchiveHeapWriter::BufferOffsetToSourceObjectTable*
  ArchiveHeapWriter::_buffer_offset_to_source_obj_table = nullptr;

void ArchiveHeapWriter::init() {
  if (HeapShared::can_write()) {
    Universe::heap()->collect(GCCause::_java_lang_system_gc);

    _buffer_offset_to_source_obj_table = new BufferOffsetToSourceObjectTable();

    _requested_open_region_bottom = nullptr;
    _requested_open_region_top = nullptr;
    _requested_closed_region_bottom = nullptr;
    _requested_closed_region_top = nullptr;

    _native_pointers = new GrowableArrayCHeap<NativePointerInfo, mtClassShared>(2048);
    _source_objs = new GrowableArrayCHeap<oop, mtClassShared>(10000);

    guarantee(UseG1GC, "implementation limitation");
    guarantee(MIN_GC_REGION_ALIGNMENT <= /*G1*/HeapRegion::min_region_size_in_words() * HeapWordSize, "must be");
  }
}

void ArchiveHeapWriter::add_source_obj(oop src_obj) {
  _source_objs->append(src_obj);
}

// For the time being, always support two regions (to be strictly compatible with existing G1
// mapping code. We might eventually use a single region (JDK-8298048).
void ArchiveHeapWriter::write(GrowableArrayCHeap<oop, mtClassShared>* roots,
                              GrowableArray<MemRegion>* closed_regions, GrowableArray<MemRegion>* open_regions,
                              GrowableArray<ArchiveHeapBitmapInfo>* closed_bitmaps,
                              GrowableArray<ArchiveHeapBitmapInfo>* open_bitmaps) {
  assert(HeapShared::can_write(), "sanity");
  allocate_buffer();
  copy_source_objs_to_buffer(roots);
  set_requested_address_for_regions(closed_regions, open_regions);
  relocate_embedded_oops(roots, closed_bitmaps, open_bitmaps);
}

bool ArchiveHeapWriter::is_too_large_to_archive(oop o) {
  return is_too_large_to_archive(o->size());
}

bool ArchiveHeapWriter::is_string_too_large_to_archive(oop string) {
  typeArrayOop value = java_lang_String::value_no_keepalive(string);
  return is_too_large_to_archive(value);
}

bool ArchiveHeapWriter::is_too_large_to_archive(size_t size) {
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

// Various lookup functions between source_obj, buffered_obj and requested_obj
bool ArchiveHeapWriter::is_in_requested_regions(oop o) {
  assert(_requested_open_region_bottom != nullptr, "do not call before this is initialized");
  assert(_requested_closed_region_bottom != nullptr, "do not call before this is initialized");

  address a = cast_from_oop<address>(o);
  return (_requested_open_region_bottom <= a && a < _requested_open_region_top) ||
         (_requested_closed_region_bottom <= a && a < _requested_closed_region_top);
}

oop ArchiveHeapWriter::requested_obj_from_buffer_offset(size_t offset) {
  oop req_obj = cast_to_oop(_requested_open_region_bottom + offset);
  assert(is_in_requested_regions(req_obj), "must be");
  return req_obj;
}

oop ArchiveHeapWriter::source_obj_to_requested_obj(oop src_obj) {
  assert(DumpSharedSpaces, "dump-time only");
  HeapShared::CachedOopInfo* p = HeapShared::archived_object_cache()->get(src_obj);
  if (p != nullptr) {
    return requested_obj_from_buffer_offset(p->buffer_offset());
  } else {
    return nullptr;
  }
}

oop ArchiveHeapWriter::buffered_addr_to_source_obj(address buffered_addr) {
  oop* p = _buffer_offset_to_source_obj_table->get(buffered_address_to_offset(buffered_addr));
  if (p != nullptr) {
    return *p;
  } else {
    return nullptr;
  }
}

address ArchiveHeapWriter::buffered_addr_to_requested_addr(address buffered_addr) {
  return _requested_open_region_bottom + buffered_address_to_offset(buffered_addr);
}

oop ArchiveHeapWriter::heap_roots_requested_address() {
  return cast_to_oop(_requested_open_region_bottom + _heap_roots_bottom);
}

address ArchiveHeapWriter::heap_region_requested_bottom(int heap_region_idx) {
  assert(_buffer != nullptr, "must be initialized");
  switch (heap_region_idx) {
  case MetaspaceShared::first_closed_heap_region:
    return _requested_closed_region_bottom;
  case MetaspaceShared::first_open_heap_region:
    return _requested_open_region_bottom;
  default:
    ShouldNotReachHere();
    return nullptr;
  }
}

void ArchiveHeapWriter::allocate_buffer() {
  int initial_buffer_size = 100000;
  _buffer = new GrowableArrayCHeap<u1, mtClassShared>(initial_buffer_size);
  _open_bottom = _buffer_top = 0;
  ensure_buffer_space(1); // so that buffer_bottom() works
}

void ArchiveHeapWriter::ensure_buffer_space(size_t min_bytes) {
  // We usually have very small heaps. If we get a huge one it's probably caused by a bug.
  guarantee(min_bytes <= max_jint, "we dont support archiving more than 2G of objects");
  _buffer->at_grow(to_array_index(min_bytes));
}

void ArchiveHeapWriter::copy_roots_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots) {
  Klass* k = Universe::objectArrayKlassObj(); // already relocated to point to archived klass
  int length = roots != nullptr ? roots->length() : 0;
  _heap_roots_word_size = objArrayOopDesc::object_size(length);
  size_t byte_size = _heap_roots_word_size * HeapWordSize;
  if (byte_size >= MIN_GC_REGION_ALIGNMENT) {
    log_error(cds, heap)("roots array is too large. Please reduce the number of classes");
    vm_exit(1);
  }

  maybe_fill_gc_region_gap(byte_size);

  size_t new_top = _buffer_top + byte_size;
  ensure_buffer_space(new_top);

  HeapWord* mem = offset_to_buffered_address<HeapWord*>(_buffer_top);
  memset(mem, 0, byte_size);
  {
    // This is copied from MemAllocator::finish
    oopDesc::set_mark(mem, markWord::prototype());
    oopDesc::release_set_klass(mem, k);
  }
  {
    // This is copied from ObjArrayAllocator::initialize
    arrayOopDesc::set_length(mem, length);
  }

  objArrayOop arrayOop = objArrayOop(cast_to_oop(mem));
  for (int i = 0; i < length; i++) {
    // Do not use arrayOop->obj_at_put(i, o) as arrayOop is outside of the real heap!
    oop o = roots->at(i);
    if (UseCompressedOops) {
      * arrayOop->obj_at_addr<narrowOop>(i) = CompressedOops::encode(o);
    } else {
      * arrayOop->obj_at_addr<oop>(i) = o;
    }
  }
  log_info(cds)("archived obj roots[%d] = " SIZE_FORMAT " bytes, klass = %p, obj = %p", length, byte_size, k, mem);

  _heap_roots_bottom = _buffer_top;
  _buffer_top = new_top;
}

void ArchiveHeapWriter::copy_source_objs_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots) {
  copy_source_objs_to_buffer_by_region(/*copy_open_region=*/true);
  copy_roots_to_buffer(roots);
  _open_top = _buffer_top;

  // Align the closed region to the next G1 region
  _buffer_top = _closed_bottom = align_up(_buffer_top, HeapRegion::GrainBytes);
  copy_source_objs_to_buffer_by_region(/*copy_open_region=*/false);
  _closed_top = _buffer_top;

  log_info(cds, heap)("Size of open region   = " SIZE_FORMAT " bytes", _open_top   - _open_bottom);
  log_info(cds, heap)("Size of closed region = " SIZE_FORMAT " bytes", _closed_top - _closed_bottom);
}

void ArchiveHeapWriter::copy_source_objs_to_buffer_by_region(bool copy_open_region) {
  for (int i = 0; i < _source_objs->length(); i++) {
    oop src_obj = _source_objs->at(i);
    HeapShared::CachedOopInfo* info = HeapShared::archived_object_cache()->get(src_obj);
    assert(info != nullptr, "must be");
    if (info->in_open_region() == copy_open_region) {
      // For region-based collectors such as G1, we need to make sure that we don't have
      // an object that can possible span across two regions.
      size_t buffer_offset = copy_one_source_obj_to_buffer(src_obj);
      info->set_buffer_offset(buffer_offset);

      _buffer_offset_to_source_obj_table->put(buffer_offset, src_obj);
    }
  }
}

size_t ArchiveHeapWriter::filler_array_byte_size(int length) {
  size_t byte_size = objArrayOopDesc::object_size(length) * HeapWordSize;
  return byte_size;
}

int ArchiveHeapWriter::filler_array_length(size_t fill_bytes) {
  assert(is_object_aligned(fill_bytes), "must be");
  size_t elemSize = (UseCompressedOops ? sizeof(narrowOop) : sizeof(oop));

  int initial_length = to_array_length(fill_bytes / elemSize);
  for (int length = initial_length; length >= 0; length --) {
    size_t array_byte_size = filler_array_byte_size(length);
    if (array_byte_size == fill_bytes) {
      return length;
    }
  }

  ShouldNotReachHere();
  return -1;
}

void ArchiveHeapWriter::init_filler_array_at_buffer_top(int array_length, size_t fill_bytes) {
  assert(UseCompressedClassPointers, "Archived heap only supported for compressed klasses");
  Klass* oak = Universe::objectArrayKlassObj(); // already relocated to point to archived klass
  HeapWord* mem = offset_to_buffered_address<HeapWord*>(_buffer_top);
  memset(mem, 0, fill_bytes);
  oopDesc::set_mark(mem, markWord::prototype());
  narrowKlass nk = ArchiveBuilder::current()->get_requested_narrow_klass(oak);
  cast_to_oop(mem)->set_narrow_klass(nk);
  arrayOopDesc::set_length(mem, array_length);
}

void ArchiveHeapWriter::maybe_fill_gc_region_gap(size_t required_byte_size) {
  // We fill only with arrays (so we don't need to use a single HeapWord filler if the
  // leftover space is smaller than a zero-sized array object). Therefore, we need to
  // make sure there's enough space of min_filler_byte_size in the current region after
  // required_byte_size has been allocated. If not, fill the remainder of the current
  // region.
  size_t min_filler_byte_size = filler_array_byte_size(0);
  size_t new_top = _buffer_top + required_byte_size + min_filler_byte_size;

  const size_t cur_min_region_bottom = align_down(_buffer_top, MIN_GC_REGION_ALIGNMENT);
  const size_t next_min_region_bottom = align_down(new_top, MIN_GC_REGION_ALIGNMENT);

  if (cur_min_region_bottom != next_min_region_bottom) {
    // Make sure that no objects span across MIN_GC_REGION_ALIGNMENT. This way
    // we can map the region in any region-based collector.
    assert(next_min_region_bottom > cur_min_region_bottom, "must be");
    assert(next_min_region_bottom - cur_min_region_bottom == MIN_GC_REGION_ALIGNMENT,
           "no buffered object can be larger than %d bytes",  MIN_GC_REGION_ALIGNMENT);

    const size_t filler_end = next_min_region_bottom;
    const size_t fill_bytes = filler_end - _buffer_top;
    assert(fill_bytes > 0, "must be");
    ensure_buffer_space(filler_end);

    int array_length = filler_array_length(fill_bytes);
    log_info(cds, heap)("Inserting filler obj array of %d elements (" SIZE_FORMAT " bytes total) @ buffer offset " SIZE_FORMAT,
                        array_length, fill_bytes, _buffer_top);
    init_filler_array_at_buffer_top(array_length, fill_bytes);

    _buffer_top = filler_end;
  }
}

size_t ArchiveHeapWriter::copy_one_source_obj_to_buffer(oop src_obj) {
  assert(!is_too_large_to_archive(src_obj), "already checked");
  size_t byte_size = src_obj->size() * HeapWordSize;
  assert(byte_size > 0, "no zero-size objects");

  maybe_fill_gc_region_gap(byte_size);

  size_t new_top = _buffer_top + byte_size;
  assert(new_top > _buffer_top, "no wrap around");

  size_t cur_min_region_bottom = align_down(_buffer_top, MIN_GC_REGION_ALIGNMENT);
  size_t next_min_region_bottom = align_down(new_top, MIN_GC_REGION_ALIGNMENT);
  assert(cur_min_region_bottom == next_min_region_bottom, "no object should cross minimal GC region boundaries");

  ensure_buffer_space(new_top);

  address from = cast_from_oop<address>(src_obj);
  address to = offset_to_buffered_address<address>(_buffer_top);
  assert(is_object_aligned(_buffer_top), "sanity");
  assert(is_object_aligned(byte_size), "sanity");
  memcpy(to, from, byte_size);

  size_t buffered_obj_offset = _buffer_top;
  _buffer_top = new_top;

  return buffered_obj_offset;
}

void ArchiveHeapWriter::set_requested_address_for_regions(GrowableArray<MemRegion>* closed_regions,
                                                          GrowableArray<MemRegion>* open_regions) {
  assert(closed_regions->length() == 0, "must be");
  assert(open_regions->length() == 0, "must be");

  assert(UseG1GC, "must be");
  address heap_end = (address)G1CollectedHeap::heap()->reserved().end();
  log_info(cds, heap)("Heap end = %p", heap_end);

  size_t closed_region_byte_size = _closed_top - _closed_bottom;
  size_t open_region_byte_size = _open_top - _open_bottom;
  assert(closed_region_byte_size > 0, "must archived at least one object for closed region!");
  assert(open_region_byte_size > 0, "must archived at least one object for open region!");

  // The following two asserts are ensured by copy_source_objs_to_buffer_by_region().
  assert(is_aligned(_closed_bottom, HeapRegion::GrainBytes), "sanity");
  assert(is_aligned(_open_bottom, HeapRegion::GrainBytes), "sanity");

  _requested_closed_region_bottom = align_down(heap_end - closed_region_byte_size, HeapRegion::GrainBytes);
  _requested_open_region_bottom = _requested_closed_region_bottom - (_closed_bottom - _open_bottom);

  assert(is_aligned(_requested_closed_region_bottom, HeapRegion::GrainBytes), "sanity");
  assert(is_aligned(_requested_open_region_bottom, HeapRegion::GrainBytes), "sanity");

  _requested_open_region_top = _requested_open_region_bottom + (_open_top - _open_bottom);
  _requested_closed_region_top = _requested_closed_region_bottom + (_closed_top - _closed_bottom);

  assert(_requested_open_region_top <= _requested_closed_region_bottom, "no overlap");

  closed_regions->append(MemRegion(offset_to_buffered_address<HeapWord*>(_closed_bottom),
                                   offset_to_buffered_address<HeapWord*>(_closed_top)));
  open_regions->append(  MemRegion(offset_to_buffered_address<HeapWord*>(_open_bottom),
                                   offset_to_buffered_address<HeapWord*>(_open_top)));
}

// Oop relocation

template <typename T> T* ArchiveHeapWriter::requested_field_addr_in_buffer(oop requested_obj, size_t field_offset) {
  T* request_p = (T*)(cast_from_oop<address>(requested_obj) + field_offset);
  return requested_addr_to_buffered_addr(request_p);
}

template <typename T> T* ArchiveHeapWriter::requested_addr_to_buffered_addr(T* p) {
  assert(is_in_requested_regions(cast_to_oop(p)), "must be");

  address addr = address(p);
  assert(addr >= _requested_open_region_bottom, "must be");
  size_t offset = addr - _requested_open_region_bottom;
  return offset_to_buffered_address<T*>(offset);
}

template <typename T> oop ArchiveHeapWriter::load_source_field_from_requested_obj(oop requested_obj, size_t field_offset) {
  T* buffered_addr = requested_field_addr_in_buffer<T>(requested_obj, field_offset);
  oop o = load_oop_from_buffer(buffered_addr);
  assert(!in_buffer(cast_from_oop<address>(o)), "must point to source oop");
  return o;
}

template <typename T> void ArchiveHeapWriter::store_requested_field_in_requested_obj(oop requested_obj, size_t field_offset,
                                                                                   oop request_field_val) {
  T* buffered_addr = requested_field_addr_in_buffer<T>(requested_obj, field_offset);
  store_oop_in_buffer(buffered_addr, request_field_val);
}

void ArchiveHeapWriter::store_oop_in_buffer(oop* buffered_addr, oop requested_obj) {
  // Make heap content deterministic. See comments inside HeapShared::to_requested_address.
  *buffered_addr = HeapShared::to_requested_address(requested_obj);
}

void ArchiveHeapWriter::store_oop_in_buffer(narrowOop* buffered_addr, oop requested_obj) {
  // Note: HeapShared::to_requested_address() is not necessary because
  // the heap always starts at a deterministic address with UseCompressedOops==true.
  narrowOop val = CompressedOops::encode_not_null(requested_obj);
  *buffered_addr = val;
}

oop ArchiveHeapWriter::load_oop_from_buffer(oop* buffered_addr) {
  return *buffered_addr;
}

oop ArchiveHeapWriter::load_oop_from_buffer(narrowOop* buffered_addr) {
  return CompressedOops::decode(*buffered_addr);
}

template <typename T> void ArchiveHeapWriter::relocate_field_in_requested_obj(oop requested_obj, size_t field_offset) {
  oop source_referent = load_source_field_from_requested_obj<T>(requested_obj, field_offset);
  if (!CompressedOops::is_null(source_referent)) {
    oop request_referent = source_obj_to_requested_obj(source_referent);
    store_requested_field_in_requested_obj<T>(requested_obj, field_offset, request_referent);
    mark_oop_pointer<T>(requested_obj, field_offset);
  }
}

template <typename T> void ArchiveHeapWriter::mark_oop_pointer(oop requested_obj, size_t field_offset) {
  T* request_p = (T*)(cast_from_oop<address>(requested_obj) + field_offset);
  ResourceBitMap* oopmap;
  address requested_region_bottom;

  if (request_p >= (T*)_requested_closed_region_bottom) {
    assert(request_p < (T*)_requested_closed_region_top, "sanity");
    oopmap = _closed_oopmap;
    requested_region_bottom = _requested_closed_region_bottom;
  } else {
    assert(request_p >= (T*)_requested_open_region_bottom, "sanity");
    assert(request_p <  (T*)_requested_open_region_top, "sanity");
    oopmap = _open_oopmap;
    requested_region_bottom = _requested_open_region_bottom;
  }

  // Mark the pointer in the oopmap
  T* region_bottom = (T*)requested_region_bottom;
  assert(request_p >= region_bottom, "must be");
  BitMap::idx_t idx = request_p - region_bottom;
  assert(idx < oopmap->size(), "overflow");
  oopmap->set_bit(idx);
}

void ArchiveHeapWriter::update_header_for_requested_obj(oop requested_obj, oop src_obj,  Klass* src_klass) {
  assert(UseCompressedClassPointers, "Archived heap only supported for compressed klasses");
  narrowKlass nk = ArchiveBuilder::current()->get_requested_narrow_klass(src_klass);
  address buffered_addr = requested_addr_to_buffered_addr(cast_from_oop<address>(requested_obj));

  oop fake_oop = cast_to_oop(buffered_addr);
  fake_oop->set_narrow_klass(nk);

  // We need to retain the identity_hash, because it may have been used by some hashtables
  // in the shared heap. This also has the side effect of pre-initializing the
  // identity_hash for all shared objects, so they are less likely to be written
  // into during run time, increasing the potential of memory sharing.
  if (src_obj != nullptr) {
    int src_hash = src_obj->identity_hash();
    fake_oop->set_mark(markWord::prototype().copy_set_hash(src_hash));
    assert(fake_oop->mark().is_unlocked(), "sanity");

    DEBUG_ONLY(int archived_hash = fake_oop->identity_hash());
    assert(src_hash == archived_hash, "Different hash codes: original %x, archived %x", src_hash, archived_hash);
  }
}

// Relocate an element in the buffered copy of HeapShared::roots()
template <typename T> void ArchiveHeapWriter::relocate_root_at(oop requested_roots, int index) {
  size_t offset = (size_t)((objArrayOop)requested_roots)->obj_at_offset<T>(index);
  relocate_field_in_requested_obj<T>(requested_roots, offset);
}

class ArchiveHeapWriter::EmbeddedOopRelocator: public BasicOopIterateClosure {
  oop _src_obj;
  oop _requested_obj;
public:
  EmbeddedOopRelocator(oop src_obj, oop requested_obj) :
    _src_obj(src_obj), _requested_obj(requested_obj) {}

  void do_oop(narrowOop *p) { EmbeddedOopRelocator::do_oop_work(p); }
  void do_oop(      oop *p) { EmbeddedOopRelocator::do_oop_work(p); }

private:
  template <class T> void do_oop_work(T *p) {
    size_t field_offset = pointer_delta(p, _src_obj, sizeof(char));
    ArchiveHeapWriter::relocate_field_in_requested_obj<T>(_requested_obj, field_offset);
  }
};

// Update all oop fields embedded in the buffered objects
void ArchiveHeapWriter::relocate_embedded_oops(GrowableArrayCHeap<oop, mtClassShared>* roots,
                                               GrowableArray<ArchiveHeapBitmapInfo>* closed_bitmaps,
                                               GrowableArray<ArchiveHeapBitmapInfo>* open_bitmaps) {
  size_t oopmap_unit = (UseCompressedOops ? sizeof(narrowOop) : sizeof(oop));
  size_t closed_region_byte_size = _closed_top - _closed_bottom;
  size_t open_region_byte_size   = _open_top   - _open_bottom;
  ResourceBitMap closed_oopmap(closed_region_byte_size / oopmap_unit);
  ResourceBitMap open_oopmap  (open_region_byte_size   / oopmap_unit);

  _closed_oopmap = &closed_oopmap;
  _open_oopmap = &open_oopmap;

  auto iterator = [&] (oop src_obj, HeapShared::CachedOopInfo& info) {
    oop requested_obj = requested_obj_from_buffer_offset(info.buffer_offset());
    update_header_for_requested_obj(requested_obj, src_obj, src_obj->klass());
    EmbeddedOopRelocator relocator(src_obj, requested_obj);

    src_obj->oop_iterate(&relocator);
  };
  HeapShared::archived_object_cache()->iterate_all(iterator);

  oop requested_roots = requested_obj_from_buffer_offset(_heap_roots_bottom);
  update_header_for_requested_obj(requested_roots, nullptr, Universe::objectArrayKlassObj());
  int length = roots != nullptr ? roots->length() : 0;
  for (int i = 0; i < length; i++) {
    if (UseCompressedOops) {
      relocate_root_at<narrowOop>(requested_roots, i);
    } else {
      relocate_root_at<oop>(requested_roots, i);
    }
  }

  closed_bitmaps->append(make_bitmap_info(&closed_oopmap, /*is_open=*/false, /*is_oopmap=*/true));
  open_bitmaps  ->append(make_bitmap_info(&open_oopmap,   /*is_open=*/false, /*is_oopmap=*/true));

  closed_bitmaps->append(compute_ptrmap(/*is_open=*/false));
  open_bitmaps  ->append(compute_ptrmap(/*is_open=*/true));

  _closed_oopmap = nullptr;
  _open_oopmap = nullptr;
}

ArchiveHeapBitmapInfo ArchiveHeapWriter::make_bitmap_info(ResourceBitMap* bitmap, bool is_open,  bool is_oopmap) {
  size_t size_in_bits = bitmap->size();
  size_t size_in_bytes;
  uintptr_t* buffer;

  if (size_in_bits > 0) {
    size_in_bytes = bitmap->size_in_bytes();
    buffer = (uintptr_t*)NEW_C_HEAP_ARRAY(char, size_in_bytes, mtInternal);
    bitmap->write_to(buffer, size_in_bytes);
  } else {
    size_in_bytes = 0;
    buffer = nullptr;
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

void ArchiveHeapWriter::mark_native_pointer(oop src_obj, int field_offset) {
  Metadata* ptr = src_obj->metadata_field_acquire(field_offset);
  if (ptr != nullptr) {
    NativePointerInfo info;
    info._src_obj = src_obj;
    info._field_offset = field_offset;
    _native_pointers->append(info);
  }
}

ArchiveHeapBitmapInfo ArchiveHeapWriter::compute_ptrmap(bool is_open) {
  int num_non_null_ptrs = 0;
  Metadata** bottom = (Metadata**) (is_open ? _requested_open_region_bottom: _requested_closed_region_bottom);
  Metadata** top = (Metadata**) (is_open ? _requested_open_region_top: _requested_closed_region_top); // exclusive
  ResourceBitMap ptrmap(top - bottom);

  for (int i = 0; i < _native_pointers->length(); i++) {
    NativePointerInfo info = _native_pointers->at(i);
    oop src_obj = info._src_obj;
    int field_offset = info._field_offset;
    HeapShared::CachedOopInfo* p = HeapShared::archived_object_cache()->get(src_obj);
    if (p->in_open_region() == is_open) {
      // requested_field_addr = the address of this field in the requested space
      oop requested_obj = requested_obj_from_buffer_offset(p->buffer_offset());
      Metadata** requested_field_addr = (Metadata**)(cast_from_oop<address>(requested_obj) + field_offset);
      assert(bottom <= requested_field_addr && requested_field_addr < top, "range check");

      // Mark this field in the bitmap
      BitMap::idx_t idx = requested_field_addr - bottom;
      ptrmap.set_bit(idx);
      num_non_null_ptrs ++;

      // Set the native pointer to the requested address of the metadata (at runtime, the metadata will have
      // this address if the RO/RW regions are mapped at the default location).

      Metadata** buffered_field_addr = requested_addr_to_buffered_addr(requested_field_addr);
      Metadata* native_ptr = *buffered_field_addr;
      assert(native_ptr != nullptr, "sanity");

      address buffered_native_ptr = ArchiveBuilder::current()->get_buffered_addr((address)native_ptr);
      address requested_native_ptr = ArchiveBuilder::current()->to_requested(buffered_native_ptr);
      *buffered_field_addr = (Metadata*)requested_native_ptr;
    }
  }

  log_info(cds, heap)("compute_ptrmap: marked %d non-null native pointers for %s heap region",
                      num_non_null_ptrs, is_open ? "open" : "closed");

  if (num_non_null_ptrs == 0) {
    ResourceBitMap empty;
    return make_bitmap_info(&empty, is_open, /*is_oopmap=*/ false);
  } else {
    return make_bitmap_info(&ptrmap, is_open, /*is_oopmap=*/ false);
  }
}

#endif // INCLUDE_CDS_JAVA_HEAP
