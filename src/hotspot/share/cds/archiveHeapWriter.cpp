/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotReferenceObjSupport.hpp"
#include "cds/archiveHeapWriter.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/filemap.hpp"
#include "cds/heapShared.hpp"
#include "cds/regeneratedClasses.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/modules.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/oopFactory.hpp"
#include "memory/universe.hpp"
#include "oops/compressedOops.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/typeArrayKlass.hpp"
#include "oops/typeArrayOop.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/bitMap.inline.hpp"
#if INCLUDE_G1GC
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#endif

#if INCLUDE_CDS_JAVA_HEAP

GrowableArrayCHeap<u1, mtClassShared>* ArchiveHeapWriter::_buffer = nullptr;

// The following are offsets from buffer_bottom()
size_t ArchiveHeapWriter::_buffer_used;

// Heap root segments
HeapRootSegments ArchiveHeapWriter::_heap_root_segments;

address ArchiveHeapWriter::_requested_bottom;
address ArchiveHeapWriter::_requested_top;

GrowableArrayCHeap<ArchiveHeapWriter::NativePointerInfo, mtClassShared>* ArchiveHeapWriter::_native_pointers;
GrowableArrayCHeap<oop, mtClassShared>* ArchiveHeapWriter::_source_objs;
GrowableArrayCHeap<ArchiveHeapWriter::HeapObjOrder, mtClassShared>* ArchiveHeapWriter::_source_objs_order;

ArchiveHeapWriter::BufferOffsetToSourceObjectTable*
  ArchiveHeapWriter::_buffer_offset_to_source_obj_table = nullptr;


typedef ResourceHashtable<
      size_t,    // offset of a filler from ArchiveHeapWriter::buffer_bottom()
      size_t,    // size of this filler (in bytes)
      127,       // prime number
      AnyObj::C_HEAP,
      mtClassShared> FillersTable;
static FillersTable* _fillers;
static int _num_native_ptrs = 0;

void ArchiveHeapWriter::init() {
  if (CDSConfig::is_dumping_heap()) {
    Universe::heap()->collect(GCCause::_java_lang_system_gc);

    _buffer_offset_to_source_obj_table = new BufferOffsetToSourceObjectTable(/*size (prime)*/36137, /*max size*/1 * M);
    _fillers = new FillersTable();
    _requested_bottom = nullptr;
    _requested_top = nullptr;

    _native_pointers = new GrowableArrayCHeap<NativePointerInfo, mtClassShared>(2048);
    _source_objs = new GrowableArrayCHeap<oop, mtClassShared>(10000);

    guarantee(MIN_GC_REGION_ALIGNMENT <= G1HeapRegion::min_region_size_in_words() * HeapWordSize, "must be");
  }
}

void ArchiveHeapWriter::add_source_obj(oop src_obj) {
  _source_objs->append(src_obj);
}

void ArchiveHeapWriter::write(GrowableArrayCHeap<oop, mtClassShared>* roots,
                              ArchiveHeapInfo* heap_info) {
  assert(CDSConfig::is_dumping_heap(), "sanity");
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
  assert(CDSConfig::is_dumping_heap(), "dump-time only");
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

objArrayOop ArchiveHeapWriter::allocate_root_segment(size_t offset, int element_count) {
  HeapWord* mem = offset_to_buffered_address<HeapWord *>(offset);
  memset(mem, 0, objArrayOopDesc::object_size(element_count));

  // The initialization code is copied from MemAllocator::finish and ObjArrayAllocator::initialize.
  if (UseCompactObjectHeaders) {
    oopDesc::release_set_mark(mem, Universe::objectArrayKlass()->prototype_header());
  } else {
    oopDesc::set_mark(mem, markWord::prototype());
    oopDesc::release_set_klass(mem, Universe::objectArrayKlass());
  }
  arrayOopDesc::set_length(mem, element_count);
  return objArrayOop(cast_to_oop(mem));
}

void ArchiveHeapWriter::root_segment_at_put(objArrayOop segment, int index, oop root) {
  // Do not use arrayOop->obj_at_put(i, o) as arrayOop is outside the real heap!
  if (UseCompressedOops) {
    *segment->obj_at_addr<narrowOop>(index) = CompressedOops::encode(root);
  } else {
    *segment->obj_at_addr<oop>(index) = root;
  }
}

void ArchiveHeapWriter::copy_roots_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots) {
  // Depending on the number of classes we are archiving, a single roots array may be
  // larger than MIN_GC_REGION_ALIGNMENT. Roots are allocated first in the buffer, which
  // allows us to chop the large array into a series of "segments". Current layout
  // starts with zero or more segments exactly fitting MIN_GC_REGION_ALIGNMENT, and end
  // with a single segment that may be smaller than MIN_GC_REGION_ALIGNMENT.
  // This is simple and efficient. We do not need filler objects anywhere between the segments,
  // or immediately after the last segment. This allows starting the object dump immediately
  // after the roots.

  assert((_buffer_used % MIN_GC_REGION_ALIGNMENT) == 0,
         "Pre-condition: Roots start at aligned boundary: %zu", _buffer_used);

  int max_elem_count = ((MIN_GC_REGION_ALIGNMENT - arrayOopDesc::header_size_in_bytes()) / heapOopSize);
  assert(objArrayOopDesc::object_size(max_elem_count)*HeapWordSize == MIN_GC_REGION_ALIGNMENT,
         "Should match exactly");

  HeapRootSegments segments(_buffer_used,
                            roots->length(),
                            MIN_GC_REGION_ALIGNMENT,
                            max_elem_count);

  int root_index = 0;
  for (size_t seg_idx = 0; seg_idx < segments.count(); seg_idx++) {
    int size_elems = segments.size_in_elems(seg_idx);
    size_t size_bytes = segments.size_in_bytes(seg_idx);

    size_t oop_offset = _buffer_used;
    _buffer_used = oop_offset + size_bytes;
    ensure_buffer_space(_buffer_used);

    assert((oop_offset % MIN_GC_REGION_ALIGNMENT) == 0,
           "Roots segment %zu start is not aligned: %zu",
           segments.count(), oop_offset);

    objArrayOop seg_oop = allocate_root_segment(oop_offset, size_elems);
    for (int i = 0; i < size_elems; i++) {
      root_segment_at_put(seg_oop, i, roots->at(root_index++));
    }

    log_info(aot, heap)("archived obj root segment [%d] = %zu bytes, obj = " PTR_FORMAT,
                        size_elems, size_bytes, p2i(seg_oop));
  }

  assert(root_index == roots->length(), "Post-condition: All roots are handled");

  _heap_root_segments = segments;
}

// The goal is to sort the objects in increasing order of:
// - objects that have only oop pointers
// - objects that have both native and oop pointers
// - objects that have only native pointers
// - objects that have no pointers
static int oop_sorting_rank(oop o) {
  bool has_oop_ptr, has_native_ptr;
  HeapShared::get_pointer_info(o, has_oop_ptr, has_native_ptr);

  if (has_oop_ptr) {
    if (!has_native_ptr) {
      return 0;
    } else {
      return 1;
    }
  } else {
    if (has_native_ptr) {
      return 2;
    } else {
      return 3;
    }
  }
}

int ArchiveHeapWriter::compare_objs_by_oop_fields(HeapObjOrder* a, HeapObjOrder* b) {
  int rank_a = a->_rank;
  int rank_b = b->_rank;

  if (rank_a != rank_b) {
    return rank_a - rank_b;
  } else {
    // If they are the same rank, sort them by their position in the _source_objs array
    return a->_index - b->_index;
  }
}

void ArchiveHeapWriter::sort_source_objs() {
  log_info(aot)("sorting heap objects");
  int len = _source_objs->length();
  _source_objs_order = new GrowableArrayCHeap<HeapObjOrder, mtClassShared>(len);

  for (int i = 0; i < len; i++) {
    oop o = _source_objs->at(i);
    int rank = oop_sorting_rank(o);
    HeapObjOrder os = {i, rank};
    _source_objs_order->append(os);
  }
  log_info(aot)("computed ranks");
  _source_objs_order->sort(compare_objs_by_oop_fields);
  log_info(aot)("sorting heap objects done");
}

void ArchiveHeapWriter::copy_source_objs_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots) {
  // There could be multiple root segments, which we want to be aligned by region.
  // Putting them ahead of objects makes sure we waste no space.
  copy_roots_to_buffer(roots);

  sort_source_objs();
  for (int i = 0; i < _source_objs_order->length(); i++) {
    int src_obj_index = _source_objs_order->at(i)._index;
    oop src_obj = _source_objs->at(src_obj_index);
    HeapShared::CachedOopInfo* info = HeapShared::archived_object_cache()->get(src_obj);
    assert(info != nullptr, "must be");
    size_t buffer_offset = copy_one_source_obj_to_buffer(src_obj);
    info->set_buffer_offset(buffer_offset);

    _buffer_offset_to_source_obj_table->put_when_absent(buffer_offset, src_obj);
    _buffer_offset_to_source_obj_table->maybe_grow();

    if (java_lang_Module::is_instance(src_obj)) {
      Modules::check_archived_module_oop(src_obj);
    }
  }

  log_info(aot)("Size of heap region = %zu bytes, %d objects, %d roots, %d native ptrs",
                _buffer_used, _source_objs->length() + 1, roots->length(), _num_native_ptrs);
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
  Klass* oak = Universe::objectArrayKlass(); // already relocated to point to archived klass
  HeapWord* mem = offset_to_buffered_address<HeapWord*>(_buffer_used);
  memset(mem, 0, fill_bytes);
  narrowKlass nk = ArchiveBuilder::current()->get_requested_narrow_klass(oak);
  if (UseCompactObjectHeaders) {
    oopDesc::release_set_mark(mem, markWord::prototype().set_narrow_klass(nk));
  } else {
    oopDesc::set_mark(mem, markWord::prototype());
    cast_to_oop(mem)->set_narrow_klass(nk);
  }
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
    log_info(aot, heap)("Inserting filler obj array of %d elements (%zu bytes total) @ buffer offset %zu",
                        array_length, fill_bytes, _buffer_used);
    HeapWord* filler = init_filler_array_at_buffer_top(array_length, fill_bytes);
    _buffer_used = filler_end;
    _fillers->put(buffered_address_to_offset((address)filler), fill_bytes);
  }
}

size_t ArchiveHeapWriter::get_filler_size_at(address buffered_addr) {
  size_t* p = _fillers->get(buffered_address_to_offset(buffered_addr));
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

  size_t heap_region_byte_size = _buffer_used;
  assert(heap_region_byte_size > 0, "must archived at least one object!");

  if (UseCompressedOops) {
    if (UseG1GC) {
      address heap_end = (address)G1CollectedHeap::heap()->reserved().end();
      log_info(aot, heap)("Heap end = %p", heap_end);
      _requested_bottom = align_down(heap_end - heap_region_byte_size, G1HeapRegion::GrainBytes);
      _requested_bottom = align_down(_requested_bottom, MIN_GC_REGION_ALIGNMENT);
      assert(is_aligned(_requested_bottom, G1HeapRegion::GrainBytes), "sanity");
    } else {
      _requested_bottom = align_up(CompressedOops::begin(), MIN_GC_REGION_ALIGNMENT);
    }
  } else {
    // We always write the objects as if the heap started at this address. This
    // makes the contents of the archive heap deterministic.
    //
    // Note that at runtime, the heap address is selected by the OS, so the archive
    // heap will not be mapped at 0x10000000, and the contents need to be patched.
    _requested_bottom = align_up((address)NOCOOPS_REQUESTED_BASE, MIN_GC_REGION_ALIGNMENT);
  }

  assert(is_aligned(_requested_bottom, MIN_GC_REGION_ALIGNMENT), "sanity");

  _requested_top = _requested_bottom + _buffer_used;

  info->set_buffer_region(MemRegion(offset_to_buffered_address<HeapWord*>(0),
                                    offset_to_buffered_address<HeapWord*>(_buffer_used)));
  info->set_heap_root_segments(_heap_root_segments);
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
  if (source_referent != nullptr) {
    if (java_lang_Class::is_instance(source_referent)) {
      Klass* k = java_lang_Class::as_Klass(source_referent);
      if (RegeneratedClasses::has_been_regenerated(k)) {
        source_referent = RegeneratedClasses::get_regenerated_object(k)->java_mirror();
      }
      // When the source object points to a "real" mirror, the buffered object should point
      // to the "scratch" mirror, which has all unarchivable fields scrubbed (to be reinstated
      // at run time).
      source_referent = HeapShared::scratch_java_mirror(source_referent);
      assert(source_referent != nullptr, "must be");
    }
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
  if (UseCompactObjectHeaders) {
    fake_oop->set_mark(markWord::prototype().set_narrow_klass(nk));
  } else {
    fake_oop->set_narrow_klass(nk);
  }

  if (src_obj == nullptr) {
    return;
  }
  // We need to retain the identity_hash, because it may have been used by some hashtables
  // in the shared heap.
  if (!src_obj->fast_no_hash_check()) {
    intptr_t src_hash = src_obj->identity_hash();
    if (UseCompactObjectHeaders) {
      fake_oop->set_mark(markWord::prototype().set_narrow_klass(nk).copy_set_hash(src_hash));
    } else {
      fake_oop->set_mark(markWord::prototype().copy_set_hash(src_hash));
    }
    assert(fake_oop->mark().is_unlocked(), "sanity");

    DEBUG_ONLY(intptr_t archived_hash = fake_oop->identity_hash());
    assert(src_hash == archived_hash, "Different hash codes: original " INTPTR_FORMAT ", archived " INTPTR_FORMAT, src_hash, archived_hash);
  }
  // Strip age bits.
  fake_oop->set_mark(fake_oop->mark().set_age(0));
}

class ArchiveHeapWriter::EmbeddedOopRelocator: public BasicOopIterateClosure {
  oop _src_obj;
  address _buffered_obj;
  CHeapBitMap* _oopmap;
  bool _is_java_lang_ref;
public:
  EmbeddedOopRelocator(oop src_obj, address buffered_obj, CHeapBitMap* oopmap) :
    _src_obj(src_obj), _buffered_obj(buffered_obj), _oopmap(oopmap)
  {
    _is_java_lang_ref = AOTReferenceObjSupport::check_if_ref_obj(src_obj);
  }

  void do_oop(narrowOop *p) { EmbeddedOopRelocator::do_oop_work(p); }
  void do_oop(      oop *p) { EmbeddedOopRelocator::do_oop_work(p); }

private:
  template <class T> void do_oop_work(T *p) {
    int field_offset = pointer_delta_as_int((char*)p, cast_from_oop<char*>(_src_obj));
    T* field_addr = (T*)(_buffered_obj + field_offset);
    if (_is_java_lang_ref && AOTReferenceObjSupport::skip_field(field_offset)) {
      // Do not copy these fields. Set them to null
      *field_addr = (T)0x0;
    } else {
      ArchiveHeapWriter::relocate_field_in_buffer<T>(field_addr, _oopmap);
    }
  }
};

static void log_bitmap_usage(const char* which, BitMap* bitmap, size_t total_bits) {
  // The whole heap is covered by total_bits, but there are only non-zero bits within [start ... end).
  size_t start = bitmap->find_first_set_bit(0);
  size_t end = bitmap->size();
  log_info(aot)("%s = %7zu ... %7zu (%3zu%% ... %3zu%% = %3zu%%)", which,
                start, end,
                start * 100 / total_bits,
                end * 100 / total_bits,
                (end - start) * 100 / total_bits);
}

// Update all oop fields embedded in the buffered objects
void ArchiveHeapWriter::relocate_embedded_oops(GrowableArrayCHeap<oop, mtClassShared>* roots,
                                               ArchiveHeapInfo* heap_info) {
  size_t oopmap_unit = (UseCompressedOops ? sizeof(narrowOop) : sizeof(oop));
  size_t heap_region_byte_size = _buffer_used;
  heap_info->oopmap()->resize(heap_region_byte_size   / oopmap_unit);

  for (int i = 0; i < _source_objs_order->length(); i++) {
    int src_obj_index = _source_objs_order->at(i)._index;
    oop src_obj = _source_objs->at(src_obj_index);
    HeapShared::CachedOopInfo* info = HeapShared::archived_object_cache()->get(src_obj);
    assert(info != nullptr, "must be");
    oop requested_obj = requested_obj_from_buffer_offset(info->buffer_offset());
    update_header_for_requested_obj(requested_obj, src_obj, src_obj->klass());
    address buffered_obj = offset_to_buffered_address<address>(info->buffer_offset());
    EmbeddedOopRelocator relocator(src_obj, buffered_obj, heap_info->oopmap());
    src_obj->oop_iterate(&relocator);
  };

  // Relocate HeapShared::roots(), which is created in copy_roots_to_buffer() and
  // doesn't have a corresponding src_obj, so we can't use EmbeddedOopRelocator on it.
  for (size_t seg_idx = 0; seg_idx < _heap_root_segments.count(); seg_idx++) {
    size_t seg_offset = _heap_root_segments.segment_offset(seg_idx);

    objArrayOop requested_obj = (objArrayOop)requested_obj_from_buffer_offset(seg_offset);
    update_header_for_requested_obj(requested_obj, nullptr, Universe::objectArrayKlass());
    address buffered_obj = offset_to_buffered_address<address>(seg_offset);
    int length = _heap_root_segments.size_in_elems(seg_idx);

    if (UseCompressedOops) {
      for (int i = 0; i < length; i++) {
        narrowOop* addr = (narrowOop*)(buffered_obj + objArrayOopDesc::obj_at_offset<narrowOop>(i));
        relocate_field_in_buffer<narrowOop>(addr, heap_info->oopmap());
      }
    } else {
      for (int i = 0; i < length; i++) {
        oop* addr = (oop*)(buffered_obj + objArrayOopDesc::obj_at_offset<oop>(i));
        relocate_field_in_buffer<oop>(addr, heap_info->oopmap());
      }
    }
  }

  compute_ptrmap(heap_info);

  size_t total_bytes = (size_t)_buffer->length();
  log_bitmap_usage("oopmap", heap_info->oopmap(), total_bytes / (UseCompressedOops ? sizeof(narrowOop) : sizeof(oop)));
  log_bitmap_usage("ptrmap", heap_info->ptrmap(), total_bytes / sizeof(address));
}

void ArchiveHeapWriter::mark_native_pointer(oop src_obj, int field_offset) {
  Metadata* ptr = src_obj->metadata_field_acquire(field_offset);
  if (ptr != nullptr) {
    NativePointerInfo info;
    info._src_obj = src_obj;
    info._field_offset = field_offset;
    _native_pointers->append(info);
    HeapShared::set_has_native_pointers(src_obj);
    _num_native_ptrs ++;
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
  // Leading zeros have been removed so some addresses may not be in the ptrmap
  size_t start_pos = FileMapInfo::current_info()->heap_ptrmap_start_pos();
  if (idx < start_pos) {
    return false;
  } else {
    idx -= start_pos;
  }
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
    guarantee(native_ptr != nullptr, "sanity");

    if (RegeneratedClasses::has_been_regenerated(native_ptr)) {
      native_ptr = RegeneratedClasses::get_regenerated_object(native_ptr);
    }

    guarantee(ArchiveBuilder::current()->has_been_buffered((address)native_ptr),
              "Metadata %p should have been archived", native_ptr);

    address buffered_native_ptr = ArchiveBuilder::current()->get_buffered_addr((address)native_ptr);
    address requested_native_ptr = ArchiveBuilder::current()->to_requested(buffered_native_ptr);
    *buffered_field_addr = (Metadata*)requested_native_ptr;
  }

  heap_info->ptrmap()->resize(max_idx + 1);
  log_info(aot, heap)("calculate_ptrmap: marked %d non-null native pointers for heap region (%zu bits)",
                      num_non_null_ptrs, size_t(heap_info->ptrmap()->size()));
}

#endif // INCLUDE_CDS_JAVA_HEAP
