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
#include "classfile/systemDictionary.hpp"
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

GrowableArrayCHeap<u1, mtClassShared>* ArchiveHeapWriter::_buffer = nullptr;

// The following are offsets from buffer_bottom()
size_t ArchiveHeapWriter::_buffer_used;
size_t ArchiveHeapWriter::_heap_roots_offset;

size_t ArchiveHeapWriter::_heap_roots_word_size;

address ArchiveHeapWriter::_requested_bottom;
address ArchiveHeapWriter::_requested_top;

GrowableArrayCHeap<ArchiveHeapWriter::NativePointerInfo, mtClassShared>* ArchiveHeapWriter::_native_pointers;
GrowableArrayCHeap<oop, mtClassShared>* ArchiveHeapWriter::_source_objs;

ArchiveHeapWriter::BufferOffsetToSourceObjectTable*
  ArchiveHeapWriter::_buffer_offset_to_source_obj_table = nullptr;


typedef ResourceHashtable<address, size_t,
      127, // prime number
      AnyObj::C_HEAP,
      mtClassShared> FillersTable;
static FillersTable* _fillers;

void ArchiveHeapWriter::init() {
  if (HeapShared::can_write()) {
    Universe::heap()->collect(GCCause::_java_lang_system_gc);

    _buffer_offset_to_source_obj_table = new BufferOffsetToSourceObjectTable();
    _fillers = new FillersTable();
    _requested_bottom = nullptr;
    _requested_top = nullptr;

    _native_pointers = new GrowableArrayCHeap<NativePointerInfo, mtClassShared>(2048);
    _source_objs = new GrowableArrayCHeap<oop, mtClassShared>(10000);

    guarantee(UseG1GC, "implementation limitation");
    guarantee(MIN_GC_REGION_ALIGNMENT <= /*G1*/HeapRegion::min_region_size_in_words() * HeapWordSize, "must be");
  }
}

void ArchiveHeapWriter::add_source_obj(oop src_obj) {
  _source_objs->append(src_obj);
}

void ArchiveHeapWriter::write(GrowableArrayCHeap<oop, mtClassShared>* roots,
                              ArchiveHeapInfo* heap_info) {
  assert(HeapShared::can_write(), "sanity");
  allocate_buffer();
  copy_source_objs_to_buffer(roots);
  set_requested_address(heap_info);
  relocate_embedded_oops(roots, heap_info);
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
bool ArchiveHeapWriter::is_in_requested_range(oop o) {
  assert(_requested_bottom != nullptr, "do not call before _requested_bottom is initialized");
  address a = cast_from_oop<address>(o);
  return (_requested_bottom <= a && a < _requested_top);
}

oop ArchiveHeapWriter::requested_obj_from_buffer_offset(size_t offset) {
  oop req_obj = cast_to_oop(_requested_bottom + offset);
  assert(is_in_requested_range(req_obj), "must be");
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
  return _requested_bottom + buffered_address_to_offset(buffered_addr);
}

oop ArchiveHeapWriter::heap_roots_requested_address() {
  return cast_to_oop(_requested_bottom + _heap_roots_offset);
}

address ArchiveHeapWriter::requested_address() {
  assert(_buffer != nullptr, "must be initialized");
  return _requested_bottom;
}

void ArchiveHeapWriter::allocate_buffer() {
  int initial_buffer_size = 100000;
  _buffer = new GrowableArrayCHeap<u1, mtClassShared>(initial_buffer_size);
  _buffer_used = 0;
  ensure_buffer_space(1); // so that buffer_bottom() works
}

void ArchiveHeapWriter::ensure_buffer_space(size_t min_bytes) {
  // We usually have very small heaps. If we get a huge one it's probably caused by a bug.
  guarantee(min_bytes <= max_jint, "we dont support archiving more than 2G of objects");
  _buffer->at_grow(to_array_index(min_bytes));
}

void ArchiveHeapWriter::copy_roots_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots) {
  Klass* k = Universe::objectArrayKlassObj(); // already relocated to point to archived klass
  int length = roots->length();
  _heap_roots_word_size = objArrayOopDesc::object_size(length);
  size_t byte_size = _heap_roots_word_size * HeapWordSize;
  if (byte_size >= MIN_GC_REGION_ALIGNMENT) {
    log_error(cds, heap)("roots array is too large. Please reduce the number of classes");
    vm_exit(1);
  }

  maybe_fill_gc_region_gap(byte_size);

  size_t new_used = _buffer_used + byte_size;
  ensure_buffer_space(new_used);

  HeapWord* mem = offset_to_buffered_address<HeapWord*>(_buffer_used);
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
  log_info(cds, heap)("archived obj roots[%d] = " SIZE_FORMAT " bytes, klass = %p, obj = %p", length, byte_size, k, mem);

  _heap_roots_offset = _buffer_used;
  _buffer_used = new_used;
}

void ArchiveHeapWriter::copy_source_objs_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots) {
  for (int i = 0; i < _source_objs->length(); i++) {
    oop src_obj = _source_objs->at(i);
    HeapShared::CachedOopInfo* info = HeapShared::archived_object_cache()->get(src_obj);
    assert(info != nullptr, "must be");
    size_t buffer_offset = copy_one_source_obj_to_buffer(src_obj);
    info->set_buffer_offset(buffer_offset);

    _buffer_offset_to_source_obj_table->put(buffer_offset, src_obj);
  }

  copy_roots_to_buffer(roots);

  log_info(cds)("Size of heap region = " SIZE_FORMAT " bytes, %d objects, %d roots",
                _buffer_used, _source_objs->length() + 1, roots->length());
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

HeapWord* ArchiveHeapWriter::init_filler_array_at_buffer_top(int array_length, size_t fill_bytes) {
  assert(UseCompressedClassPointers, "Archived heap only supported for compressed klasses");
  Klass* oak = Universe::objectArrayKlassObj(); // already relocated to point to archived klass
  HeapWord* mem = offset_to_buffered_address<HeapWord*>(_buffer_used);
  memset(mem, 0, fill_bytes);
  oopDesc::set_mark(mem, markWord::prototype());
  narrowKlass nk = ArchiveBuilder::current()->get_requested_narrow_klass(oak);
  cast_to_oop(mem)->set_narrow_klass(nk);
  arrayOopDesc::set_length(mem, array_length);
  return mem;
}

void ArchiveHeapWriter::maybe_fill_gc_region_gap(size_t required_byte_size) {
  // We fill only with arrays (so we don't need to use a single HeapWord filler if the
  // leftover space is smaller than a zero-sized array object). Therefore, we need to
  // make sure there's enough space of min_filler_byte_size in the current region after
  // required_byte_size has been allocated. If not, fill the remainder of the current
  // region.
  size_t min_filler_byte_size = filler_array_byte_size(0);
  size_t new_used = _buffer_used + required_byte_size + min_filler_byte_size;

  const size_t cur_min_region_bottom = align_down(_buffer_used, MIN_GC_REGION_ALIGNMENT);
  const size_t next_min_region_bottom = align_down(new_used, MIN_GC_REGION_ALIGNMENT);

  if (cur_min_region_bottom != next_min_region_bottom) {
    // Make sure that no objects span across MIN_GC_REGION_ALIGNMENT. This way
    // we can map the region in any region-based collector.
    assert(next_min_region_bottom > cur_min_region_bottom, "must be");
    assert(next_min_region_bottom - cur_min_region_bottom == MIN_GC_REGION_ALIGNMENT,
           "no buffered object can be larger than %d bytes",  MIN_GC_REGION_ALIGNMENT);

    const size_t filler_end = next_min_region_bottom;
    const size_t fill_bytes = filler_end - _buffer_used;
    assert(fill_bytes > 0, "must be");
    ensure_buffer_space(filler_end);

    int array_length = filler_array_length(fill_bytes);
    log_info(cds, heap)("Inserting filler obj array of %d elements (" SIZE_FORMAT " bytes total) @ buffer offset " SIZE_FORMAT,
                        array_length, fill_bytes, _buffer_used);
    HeapWord* filler = init_filler_array_at_buffer_top(array_length, fill_bytes);
    _buffer_used = filler_end;
    _fillers->put((address)filler, fill_bytes);
  }
}

size_t ArchiveHeapWriter::get_filler_size_at(address buffered_addr) {
  size_t* p = _fillers->get(buffered_addr);
  if (p != nullptr) {
    assert(*p > 0, "filler must be larger than zero bytes");
    return *p;
  } else {
    return 0; // buffered_addr is not a filler
  }
}

template <typename T>
void update_buffered_object_field(address buffered_obj, int field_offset, T value) {
  T* field_addr = cast_to_oop(buffered_obj)->field_addr<T>(field_offset);
  *field_addr = value;
}

size_t ArchiveHeapWriter::copy_one_source_obj_to_buffer(oop src_obj) {
  assert(!is_too_large_to_archive(src_obj), "already checked");
  size_t byte_size = src_obj->size() * HeapWordSize;
  assert(byte_size > 0, "no zero-size objects");

  // For region-based collectors such as G1, the archive heap may be mapped into
  // multiple regions. We need to make sure that we don't have an object that can possible
  // span across two regions.
  maybe_fill_gc_region_gap(byte_size);

  size_t new_used = _buffer_used + byte_size;
  assert(new_used > _buffer_used, "no wrap around");

  size_t cur_min_region_bottom = align_down(_buffer_used, MIN_GC_REGION_ALIGNMENT);
  size_t next_min_region_bottom = align_down(new_used, MIN_GC_REGION_ALIGNMENT);
  assert(cur_min_region_bottom == next_min_region_bottom, "no object should cross minimal GC region boundaries");

  ensure_buffer_space(new_used);

  address from = cast_from_oop<address>(src_obj);
  address to = offset_to_buffered_address<address>(_buffer_used);
  assert(is_object_aligned(_buffer_used), "sanity");
  assert(is_object_aligned(byte_size), "sanity");
  memcpy(to, from, byte_size);

  // These native pointers will be restored explicitly at run time.
  if (java_lang_Module::is_instance(src_obj)) {
    update_buffered_object_field<ModuleEntry*>(to, java_lang_Module::module_entry_offset(), nullptr);
  } else if (java_lang_ClassLoader::is_instance(src_obj)) {
#ifdef ASSERT
    // We only archive these loaders
    if (src_obj != SystemDictionary::java_platform_loader() &&
        src_obj != SystemDictionary::java_system_loader()) {
      assert(src_obj->klass()->name()->equals("jdk/internal/loader/ClassLoaders$BootClassLoader"), "must be");
    }
#endif
    update_buffered_object_field<ClassLoaderData*>(to, java_lang_ClassLoader::loader_data_offset(), nullptr);
  }

  size_t buffered_obj_offset = _buffer_used;
  _buffer_used = new_used;

  return buffered_obj_offset;
}

void ArchiveHeapWriter::set_requested_address(ArchiveHeapInfo* info) {
  assert(!info->is_used(), "only set once");
  assert(UseG1GC, "must be");
  address heap_end = (address)G1CollectedHeap::heap()->reserved().end();
  log_info(cds, heap)("Heap end = %p", heap_end);

  size_t heap_region_byte_size = _buffer_used;
  assert(heap_region_byte_size > 0, "must archived at least one object!");


  if (UseCompressedOops) {
    _requested_bottom = align_down(heap_end - heap_region_byte_size, HeapRegion::GrainBytes);
  } else {
    // We always write the objects as if the heap started at this address. This
    // makes the contents of the archive heap deterministic.
    //
    // Note that at runtime, the heap address is selected by the OS, so the archive
    // heap will not be mapped at 0x10000000, and the contents need to be patched.
    _requested_bottom = (address)NOCOOPS_REQUESTED_BASE;
  }

  assert(is_aligned(_requested_bottom, HeapRegion::GrainBytes), "sanity");

  _requested_top = _requested_bottom + _buffer_used;

  info->set_buffer_region(MemRegion(offset_to_buffered_address<HeapWord*>(0),
                                    offset_to_buffered_address<HeapWord*>(_buffer_used)));
  info->set_heap_roots_offset(_heap_roots_offset);
}

// Oop relocation

template <typename T> T* ArchiveHeapWriter::requested_addr_to_buffered_addr(T* p) {
  assert(is_in_requested_range(cast_to_oop(p)), "must be");

  address addr = address(p);
  assert(addr >= _requested_bottom, "must be");
  size_t offset = addr - _requested_bottom;
  return offset_to_buffered_address<T*>(offset);
}

template <typename T> oop ArchiveHeapWriter::load_source_oop_from_buffer(T* buffered_addr) {
  oop o = load_oop_from_buffer(buffered_addr);
  assert(!in_buffer(cast_from_oop<address>(o)), "must point to source oop");
  return o;
}

template <typename T> void ArchiveHeapWriter::store_requested_oop_in_buffer(T* buffered_addr,
                                                                            oop request_oop) {
  assert(is_in_requested_range(request_oop), "must be");
  store_oop_in_buffer(buffered_addr, request_oop);
}

inline void ArchiveHeapWriter::store_oop_in_buffer(oop* buffered_addr, oop requested_obj) {
  *buffered_addr = requested_obj;
}

inline void ArchiveHeapWriter::store_oop_in_buffer(narrowOop* buffered_addr, oop requested_obj) {
  narrowOop val = CompressedOops::encode_not_null(requested_obj);
  *buffered_addr = val;
}

oop ArchiveHeapWriter::load_oop_from_buffer(oop* buffered_addr) {
  return *buffered_addr;
}

oop ArchiveHeapWriter::load_oop_from_buffer(narrowOop* buffered_addr) {
  return CompressedOops::decode(*buffered_addr);
}

template <typename T> void ArchiveHeapWriter::relocate_field_in_buffer(T* field_addr_in_buffer, CHeapBitMap* oopmap) {
  oop source_referent = load_source_oop_from_buffer<T>(field_addr_in_buffer);
  if (!CompressedOops::is_null(source_referent)) {
    oop request_referent = source_obj_to_requested_obj(source_referent);
    store_requested_oop_in_buffer<T>(field_addr_in_buffer, request_referent);
    mark_oop_pointer<T>(field_addr_in_buffer, oopmap);
  }
}

template <typename T> void ArchiveHeapWriter::mark_oop_pointer(T* buffered_addr, CHeapBitMap* oopmap) {
  T* request_p = (T*)(buffered_addr_to_requested_addr((address)buffered_addr));
  address requested_region_bottom;

  assert(request_p >= (T*)_requested_bottom, "sanity");
  assert(request_p <  (T*)_requested_top, "sanity");
  requested_region_bottom = _requested_bottom;

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
    intptr_t src_hash = src_obj->identity_hash();
    fake_oop->set_mark(markWord::prototype().copy_set_hash(src_hash));
    assert(fake_oop->mark().is_unlocked(), "sanity");

    DEBUG_ONLY(intptr_t archived_hash = fake_oop->identity_hash());
    assert(src_hash == archived_hash, "Different hash codes: original " INTPTR_FORMAT ", archived " INTPTR_FORMAT, src_hash, archived_hash);
  }
}

// Relocate an element in the buffered copy of HeapShared::roots()
template <typename T> void ArchiveHeapWriter::relocate_root_at(oop requested_roots, int index, CHeapBitMap* oopmap) {
  size_t offset = (size_t)((objArrayOop)requested_roots)->obj_at_offset<T>(index);
  relocate_field_in_buffer<T>((T*)(buffered_heap_roots_addr() + offset), oopmap);
}

class ArchiveHeapWriter::EmbeddedOopRelocator: public BasicOopIterateClosure {
  oop _src_obj;
  address _buffered_obj;
  CHeapBitMap* _oopmap;

public:
  EmbeddedOopRelocator(oop src_obj, address buffered_obj, CHeapBitMap* oopmap) :
    _src_obj(src_obj), _buffered_obj(buffered_obj), _oopmap(oopmap) {}

  void do_oop(narrowOop *p) { EmbeddedOopRelocator::do_oop_work(p); }
  void do_oop(      oop *p) { EmbeddedOopRelocator::do_oop_work(p); }

private:
  template <class T> void do_oop_work(T *p) {
    size_t field_offset = pointer_delta(p, _src_obj, sizeof(char));
    ArchiveHeapWriter::relocate_field_in_buffer<T>((T*)(_buffered_obj + field_offset), _oopmap);
  }
};

// Update all oop fields embedded in the buffered objects
void ArchiveHeapWriter::relocate_embedded_oops(GrowableArrayCHeap<oop, mtClassShared>* roots,
                                               ArchiveHeapInfo* heap_info) {
  size_t oopmap_unit = (UseCompressedOops ? sizeof(narrowOop) : sizeof(oop));
  size_t heap_region_byte_size = _buffer_used;
  heap_info->oopmap()->resize(heap_region_byte_size   / oopmap_unit);

  auto iterator = [&] (oop src_obj, HeapShared::CachedOopInfo& info) {
    oop requested_obj = requested_obj_from_buffer_offset(info.buffer_offset());
    update_header_for_requested_obj(requested_obj, src_obj, src_obj->klass());
    address buffered_obj = offset_to_buffered_address<address>(info.buffer_offset());
    EmbeddedOopRelocator relocator(src_obj, buffered_obj, heap_info->oopmap());
    src_obj->oop_iterate(&relocator);
  };
  HeapShared::archived_object_cache()->iterate_all(iterator);

  // Relocate HeapShared::roots(), which is created in copy_roots_to_buffer() and
  // doesn't have a corresponding src_obj, so we can't use EmbeddedOopRelocator on it.
  oop requested_roots = requested_obj_from_buffer_offset(_heap_roots_offset);
  update_header_for_requested_obj(requested_roots, nullptr, Universe::objectArrayKlassObj());
  int length = roots != nullptr ? roots->length() : 0;
  for (int i = 0; i < length; i++) {
    if (UseCompressedOops) {
      relocate_root_at<narrowOop>(requested_roots, i, heap_info->oopmap());
    } else {
      relocate_root_at<oop>(requested_roots, i, heap_info->oopmap());
    }
  }

  compute_ptrmap(heap_info);
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

// Do we have a jlong/jint field that's actually a pointer to a MetaspaceObj?
bool ArchiveHeapWriter::is_marked_as_native_pointer(ArchiveHeapInfo* heap_info, oop src_obj, int field_offset) {
  HeapShared::CachedOopInfo* p = HeapShared::archived_object_cache()->get(src_obj);
  assert(p != nullptr, "must be");

  // requested_field_addr = the address of this field in the requested space
  oop requested_obj = requested_obj_from_buffer_offset(p->buffer_offset());
  Metadata** requested_field_addr = (Metadata**)(cast_from_oop<address>(requested_obj) + field_offset);
  assert((Metadata**)_requested_bottom <= requested_field_addr && requested_field_addr < (Metadata**) _requested_top, "range check");

  BitMap::idx_t idx = requested_field_addr - (Metadata**) _requested_bottom;
  return (idx < heap_info->ptrmap()->size()) && (heap_info->ptrmap()->at(idx) == true);
}

void ArchiveHeapWriter::compute_ptrmap(ArchiveHeapInfo* heap_info) {
  int num_non_null_ptrs = 0;
  Metadata** bottom = (Metadata**) _requested_bottom;
  Metadata** top = (Metadata**) _requested_top; // exclusive
  heap_info->ptrmap()->resize(top - bottom);

  BitMap::idx_t max_idx = 32; // paranoid - don't make it too small
  for (int i = 0; i < _native_pointers->length(); i++) {
    NativePointerInfo info = _native_pointers->at(i);
    oop src_obj = info._src_obj;
    int field_offset = info._field_offset;
    HeapShared::CachedOopInfo* p = HeapShared::archived_object_cache()->get(src_obj);
    // requested_field_addr = the address of this field in the requested space
    oop requested_obj = requested_obj_from_buffer_offset(p->buffer_offset());
    Metadata** requested_field_addr = (Metadata**)(cast_from_oop<address>(requested_obj) + field_offset);
    assert(bottom <= requested_field_addr && requested_field_addr < top, "range check");

    // Mark this field in the bitmap
    BitMap::idx_t idx = requested_field_addr - bottom;
    heap_info->ptrmap()->set_bit(idx);
    num_non_null_ptrs ++;
    max_idx = MAX2(max_idx, idx);

    // Set the native pointer to the requested address of the metadata (at runtime, the metadata will have
    // this address if the RO/RW regions are mapped at the default location).

    Metadata** buffered_field_addr = requested_addr_to_buffered_addr(requested_field_addr);
    Metadata* native_ptr = *buffered_field_addr;
    assert(native_ptr != nullptr, "sanity");

    address buffered_native_ptr = ArchiveBuilder::current()->get_buffered_addr((address)native_ptr);
    address requested_native_ptr = ArchiveBuilder::current()->to_requested(buffered_native_ptr);
    *buffered_field_addr = (Metadata*)requested_native_ptr;
  }

  heap_info->ptrmap()->resize(max_idx + 1);
  log_info(cds, heap)("calculate_ptrmap: marked %d non-null native pointers for heap region (" SIZE_FORMAT " bits)",
                      num_non_null_ptrs, size_t(heap_info->ptrmap()->size()));
}

#endif // INCLUDE_CDS_JAVA_HEAP
