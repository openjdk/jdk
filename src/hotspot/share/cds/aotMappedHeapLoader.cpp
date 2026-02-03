/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotLogging.hpp"
#include "cds/aotMappedHeap.hpp"
#include "cds/aotMappedHeapLoader.inline.hpp"
#include "cds/aotMappedHeapWriter.hpp"
#include "cds/aotMetaspace.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.inline.hpp"
#include "classfile/classLoaderDataShared.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "logging/log.hpp"
#include "logging/logMessage.hpp"
#include "logging/logStream.hpp"
#include "logging/logTag.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "sanitizers/ub.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/copy.hpp"
#if INCLUDE_G1GC
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#endif

#if INCLUDE_CDS_JAVA_HEAP

bool AOTMappedHeapLoader::_is_mapped = false;
bool AOTMappedHeapLoader::_is_loaded = false;

bool    AOTMappedHeapLoader::_narrow_oop_base_initialized = false;
address AOTMappedHeapLoader::_narrow_oop_base;
int     AOTMappedHeapLoader::_narrow_oop_shift;

// Support for loaded heap.
uintptr_t AOTMappedHeapLoader::_loaded_heap_bottom = 0;
uintptr_t AOTMappedHeapLoader::_loaded_heap_top = 0;
uintptr_t AOTMappedHeapLoader::_dumptime_base = UINTPTR_MAX;
uintptr_t AOTMappedHeapLoader::_dumptime_top = 0;
intx AOTMappedHeapLoader::_runtime_offset = 0;
bool AOTMappedHeapLoader::_loading_failed = false;

// Support for mapped heap.
uintptr_t AOTMappedHeapLoader::_mapped_heap_bottom = 0;
bool      AOTMappedHeapLoader::_mapped_heap_relocation_initialized = false;
ptrdiff_t AOTMappedHeapLoader::_mapped_heap_delta = 0;

// Heap roots
GrowableArrayCHeap<OopHandle, mtClassShared>* AOTMappedHeapLoader::_root_segments = nullptr;
int AOTMappedHeapLoader::_root_segment_max_size_elems;

MemRegion AOTMappedHeapLoader::_mapped_heap_memregion;
bool AOTMappedHeapLoader::_heap_pointers_need_patching;

// Every mapped region is offset by _mapped_heap_delta from its requested address.
// See FileMapInfo::heap_region_requested_address().
ATTRIBUTE_NO_UBSAN
void AOTMappedHeapLoader::init_mapped_heap_info(address mapped_heap_bottom, ptrdiff_t delta, int dumptime_oop_shift) {
  assert(!_mapped_heap_relocation_initialized, "only once");
  if (!UseCompressedOops) {
    assert(dumptime_oop_shift == 0, "sanity");
  }
  assert(can_map(), "sanity");
  init_narrow_oop_decoding(CompressedOops::base() + delta, dumptime_oop_shift);
  _mapped_heap_bottom = (intptr_t)mapped_heap_bottom;
  _mapped_heap_delta = delta;
  _mapped_heap_relocation_initialized = true;
}

void AOTMappedHeapLoader::init_narrow_oop_decoding(address base, int shift) {
  assert(!_narrow_oop_base_initialized, "only once");
  _narrow_oop_base_initialized = true;
  _narrow_oop_base = base;
  _narrow_oop_shift = shift;
}

void AOTMappedHeapLoader::fixup_region() {
  FileMapInfo* mapinfo = FileMapInfo::current_info();
  if (is_mapped()) {
    fixup_mapped_heap_region(mapinfo);
  } else if (_loading_failed) {
    fill_failed_loaded_heap();
  }
}

// ------------------ Support for Region MAPPING -----------------------------------------

// Patch all the embedded oop pointers inside an archived heap region,
// to be consistent with the runtime oop encoding.
class PatchCompressedEmbeddedPointers: public BitMapClosure {
  narrowOop* _start;

 public:
  PatchCompressedEmbeddedPointers(narrowOop* start) : _start(start) {}

  bool do_bit(size_t offset) {
    narrowOop* p = _start + offset;
    narrowOop v = *p;
    assert(!CompressedOops::is_null(v), "null oops should have been filtered out at dump time");
    oop o = AOTMappedHeapLoader::decode_from_mapped_archive(v);
    RawAccess<IS_NOT_NULL>::oop_store(p, o);
    return true;
  }
};

class PatchCompressedEmbeddedPointersQuick: public BitMapClosure {
  narrowOop* _start;
  uint32_t _delta;

 public:
  PatchCompressedEmbeddedPointersQuick(narrowOop* start, uint32_t delta) : _start(start), _delta(delta) {}

  bool do_bit(size_t offset) {
    narrowOop* p = _start + offset;
    narrowOop v = *p;
    assert(!CompressedOops::is_null(v), "null oops should have been filtered out at dump time");
    narrowOop new_v = CompressedOops::narrow_oop_cast(CompressedOops::narrow_oop_value(v) + _delta);
    assert(!CompressedOops::is_null(new_v), "should never relocate to narrowOop(0)");
#ifdef ASSERT
    oop o1 = AOTMappedHeapLoader::decode_from_mapped_archive(v);
    oop o2 = CompressedOops::decode_not_null(new_v);
    assert(o1 == o2, "quick delta must work");
#endif
    RawAccess<IS_NOT_NULL>::oop_store(p, new_v);
    return true;
  }
};

class PatchUncompressedEmbeddedPointers: public BitMapClosure {
  oop* _start;
  intptr_t _delta;

 public:
  PatchUncompressedEmbeddedPointers(oop* start, intx runtime_offset) :
    _start(start),
    _delta(runtime_offset) {}

  PatchUncompressedEmbeddedPointers(oop* start) :
    _start(start),
    _delta(AOTMappedHeapLoader::mapped_heap_delta()) {}

  bool do_bit(size_t offset) {
    oop* p = _start + offset;
    intptr_t dumptime_oop = (intptr_t)((void*)*p);
    assert(dumptime_oop != 0, "null oops should have been filtered out at dump time");
    intptr_t runtime_oop = dumptime_oop + _delta;
    RawAccess<IS_NOT_NULL>::oop_store(p, cast_to_oop(runtime_oop));
    return true;
  }
};

void AOTMappedHeapLoader::patch_compressed_embedded_pointers(BitMapView bm,
                                                             FileMapInfo* info,
                                                             MemRegion region) {
  narrowOop dt_encoded_bottom = encoded_heap_region_dumptime_address(info);
  narrowOop rt_encoded_bottom = CompressedOops::encode_not_null(cast_to_oop(region.start()));
  log_info(aot)("patching heap embedded pointers: narrowOop 0x%8x -> 0x%8x",
                  (uint)dt_encoded_bottom, (uint)rt_encoded_bottom);

  // Optimization: if dumptime shift is the same as runtime shift, we can perform a
  // quick conversion from "dumptime narrowOop" -> "runtime narrowOop".
  narrowOop* patching_start = (narrowOop*)region.start() + FileMapInfo::current_info()->mapped_heap()->oopmap_start_pos();
  if (_narrow_oop_shift == CompressedOops::shift()) {
    uint32_t quick_delta = (uint32_t)rt_encoded_bottom - (uint32_t)dt_encoded_bottom;
    log_info(aot)("heap data relocation quick delta = 0x%x", quick_delta);
    if (quick_delta == 0) {
      log_info(aot)("heap data relocation unnecessary, quick_delta = 0");
    } else {
      PatchCompressedEmbeddedPointersQuick patcher(patching_start, quick_delta);
      bm.iterate(&patcher);
    }
  } else {
    log_info(aot)("heap data quick relocation not possible");
    PatchCompressedEmbeddedPointers patcher(patching_start);
    bm.iterate(&patcher);
  }
}

// Patch all the non-null pointers that are embedded in the archived heap objects
// in this (mapped) region
void AOTMappedHeapLoader::patch_embedded_pointers(FileMapInfo* info,
                                                  MemRegion region, address oopmap,
                                                  size_t oopmap_size_in_bits) {
  BitMapView bm((BitMap::bm_word_t*)oopmap, oopmap_size_in_bits);
  if (UseCompressedOops) {
    patch_compressed_embedded_pointers(bm, info, region);
  } else {
    PatchUncompressedEmbeddedPointers patcher((oop*)region.start() + FileMapInfo::current_info()->mapped_heap()->oopmap_start_pos());
    bm.iterate(&patcher);
  }
}

// ------------------ Support for Region LOADING -----------------------------------------

// The CDS archive remembers each heap object by its address at dump time, but
// the heap object may be loaded at a different address at run time. This structure is used
// to translate the dump time addresses for all objects in FileMapInfo::space_at(region_index)
// to their runtime addresses.
struct AOTMappedHeapRegion {
  int       _region_index;   // index for FileMapInfo::space_at(index)
  size_t    _region_size;    // number of bytes in this region
  uintptr_t _dumptime_base;  // The dump-time (decoded) address of the first object in this region
  intx      _runtime_offset; // If an object's dump time address P is within in this region, its
                             // runtime address is P + _runtime_offset
  uintptr_t top() {
    return _dumptime_base + _region_size;
  }
};

void AOTMappedHeapLoader::init_loaded_heap_relocation(AOTMappedHeapRegion* loaded_region) {
  _dumptime_base = loaded_region->_dumptime_base;
  _dumptime_top = loaded_region->top();
  _runtime_offset = loaded_region->_runtime_offset;
}

bool AOTMappedHeapLoader::can_load() {
  return Universe::heap()->can_load_archived_objects();
}

class AOTMappedHeapLoader::PatchLoadedRegionPointers: public BitMapClosure {
  narrowOop* _start;
  intx _offset;
  uintptr_t _base;
  uintptr_t _top;

 public:
  PatchLoadedRegionPointers(narrowOop* start, AOTMappedHeapRegion* loaded_region)
    : _start(start),
      _offset(loaded_region->_runtime_offset),
      _base(loaded_region->_dumptime_base),
      _top(loaded_region->top()) {}

  bool do_bit(size_t offset) {
    assert(UseCompressedOops, "PatchLoadedRegionPointers for uncompressed oops is unimplemented");
    narrowOop* p = _start + offset;
    narrowOop v = *p;
    assert(!CompressedOops::is_null(v), "null oops should have been filtered out at dump time");
    uintptr_t o = cast_from_oop<uintptr_t>(AOTMappedHeapLoader::decode_from_archive(v));
    assert(_base <= o && o < _top, "must be");

    o += _offset;
    AOTMappedHeapLoader::assert_in_loaded_heap(o);
    RawAccess<IS_NOT_NULL>::oop_store(p, cast_to_oop(o));
    return true;
  }
};

bool AOTMappedHeapLoader::init_loaded_region(FileMapInfo* mapinfo, AOTMappedHeapRegion* loaded_region,
                                             MemRegion& archive_space) {
  size_t total_bytes = 0;
  FileMapRegion* r = mapinfo->region_at(AOTMetaspace::hp);
  r->assert_is_heap_region();
  if (r->used() == 0) {
    return false;
  }

  assert(is_aligned(r->used(), HeapWordSize), "must be");
  total_bytes += r->used();
  loaded_region->_region_index = AOTMetaspace::hp;
  loaded_region->_region_size = r->used();
  loaded_region->_dumptime_base = (uintptr_t)heap_region_dumptime_address(mapinfo);

  assert(is_aligned(total_bytes, HeapWordSize), "must be");
  size_t word_size = total_bytes / HeapWordSize;
  HeapWord* buffer = Universe::heap()->allocate_loaded_archive_space(word_size);
  if (buffer == nullptr) {
    return false;
  }

  archive_space = MemRegion(buffer, word_size);
  _loaded_heap_bottom = (uintptr_t)archive_space.start();
  _loaded_heap_top    = _loaded_heap_bottom + total_bytes;

  loaded_region->_runtime_offset = _loaded_heap_bottom - loaded_region->_dumptime_base;

  return true;
}

bool AOTMappedHeapLoader::load_heap_region_impl(FileMapInfo* mapinfo, AOTMappedHeapRegion* loaded_region,
                                                uintptr_t load_address) {
  uintptr_t bitmap_base = (uintptr_t)mapinfo->map_bitmap_region();
  if (bitmap_base == 0) {
    _loading_failed = true;
    return false; // OOM or CRC error
  }

  FileMapRegion* r = mapinfo->region_at(loaded_region->_region_index);
  if (!mapinfo->read_region(loaded_region->_region_index, (char*)load_address, r->used(), /* do_commit = */ false)) {
    // There's no easy way to free the buffer, so we will fill it with zero later
    // in fill_failed_loaded_heap(), and it will eventually be GC'ed.
    log_warning(aot)("Loading of heap region %d has failed. Archived objects are disabled", loaded_region->_region_index);
    _loading_failed = true;
    return false;
  }
  assert(r->mapped_base() == (char*)load_address, "sanity");
  log_info(aot)("Loaded heap    region #%d at base " INTPTR_FORMAT " top " INTPTR_FORMAT
                " size %6zu delta %zd",
                loaded_region->_region_index, load_address, load_address + loaded_region->_region_size,
                loaded_region->_region_size, loaded_region->_runtime_offset);

  uintptr_t oopmap = bitmap_base + r->oopmap_offset();
  BitMapView bm((BitMap::bm_word_t*)oopmap, r->oopmap_size_in_bits());

  if (UseCompressedOops) {
    PatchLoadedRegionPointers patcher((narrowOop*)load_address + FileMapInfo::current_info()->mapped_heap()->oopmap_start_pos(), loaded_region);
    bm.iterate(&patcher);
  } else {
    PatchUncompressedEmbeddedPointers patcher((oop*)load_address + FileMapInfo::current_info()->mapped_heap()->oopmap_start_pos(), loaded_region->_runtime_offset);
    bm.iterate(&patcher);
  }
  return true;
}

bool AOTMappedHeapLoader::load_heap_region(FileMapInfo* mapinfo) {
  assert(can_load(), "loaded heap for must be supported");
  init_narrow_oop_decoding(mapinfo->narrow_oop_base(), mapinfo->narrow_oop_shift());

  AOTMappedHeapRegion loaded_region;
  memset(&loaded_region, 0, sizeof(loaded_region));

  MemRegion archive_space;
  if (!init_loaded_region(mapinfo, &loaded_region, archive_space)) {
    return false;
  }

  if (!load_heap_region_impl(mapinfo, &loaded_region, (uintptr_t)archive_space.start())) {
    assert(_loading_failed, "must be");
    return false;
  }

  init_loaded_heap_relocation(&loaded_region);
  _is_loaded = true;

  return true;
}

objArrayOop AOTMappedHeapLoader::root_segment(int segment_idx) {
  if (!CDSConfig::is_using_archive()) {
    assert(CDSConfig::is_dumping_heap() && Thread::current() == (Thread*)VMThread::vm_thread(), "sanity");
  }

  objArrayOop segment = (objArrayOop)_root_segments->at(segment_idx).resolve();
  assert(segment != nullptr, "should have been initialized");
  return segment;
}

void AOTMappedHeapLoader::get_segment_indexes(int idx, int& seg_idx, int& int_idx) {
  assert(_root_segment_max_size_elems > 0, "sanity");

  // Try to avoid divisions for the common case.
  if (idx < _root_segment_max_size_elems) {
    seg_idx = 0;
    int_idx = idx;
  } else {
    seg_idx = idx / _root_segment_max_size_elems;
    int_idx = idx % _root_segment_max_size_elems;
  }

  assert(idx == seg_idx * _root_segment_max_size_elems + int_idx,
         "sanity: %d index maps to %d segment and %d internal", idx, seg_idx, int_idx);
}

void AOTMappedHeapLoader::add_root_segment(objArrayOop segment_oop) {
  assert(segment_oop != nullptr, "must be");
  assert(is_in_use(), "must be");
  if (_root_segments == nullptr) {
    _root_segments = new GrowableArrayCHeap<OopHandle, mtClassShared>(10);
  }
  _root_segments->push(OopHandle(Universe::vm_global(), segment_oop));
}

void AOTMappedHeapLoader::init_root_segment_sizes(int max_size_elems) {
  _root_segment_max_size_elems = max_size_elems;
}

oop AOTMappedHeapLoader::get_root(int index) {
  assert(!_root_segments->is_empty(), "must have loaded shared heap");
  int seg_idx, int_idx;
  get_segment_indexes(index, seg_idx, int_idx);
  objArrayOop result = objArrayOop(root_segment(seg_idx));
  return result->obj_at(int_idx);
}

void AOTMappedHeapLoader::clear_root(int index) {
  int seg_idx, int_idx;
  get_segment_indexes(index, seg_idx, int_idx);
  root_segment(seg_idx)->obj_at_put(int_idx, nullptr);
}

class VerifyLoadedHeapEmbeddedPointers: public BasicOopIterateClosure {
  HashTable<uintptr_t, bool>* _table;

 public:
  VerifyLoadedHeapEmbeddedPointers(HashTable<uintptr_t, bool>* table) : _table(table) {}

  virtual void do_oop(narrowOop* p) {
    // This should be called before the loaded region is modified, so all the embedded pointers
    // must be null, or must point to a valid object in the loaded region.
    narrowOop v = *p;
    if (!CompressedOops::is_null(v)) {
      oop o = CompressedOops::decode_not_null(v);
      uintptr_t u = cast_from_oop<uintptr_t>(o);
      AOTMappedHeapLoader::assert_in_loaded_heap(u);
      guarantee(_table->contains(u), "must point to beginning of object in loaded archived region");
    }
  }
  virtual void do_oop(oop* p) {
    oop v = *p;
    if(v != nullptr) {
      uintptr_t u = cast_from_oop<uintptr_t>(v);
      AOTMappedHeapLoader::assert_in_loaded_heap(u);
      guarantee(_table->contains(u), "must point to beginning of object in loaded archived region");
    }
  }
};

void AOTMappedHeapLoader::finish_initialization(FileMapInfo* info) {
  patch_heap_embedded_pointers(info);

  if (is_loaded()) {
    // These operations are needed only when the heap is loaded (not mapped).
    finish_loaded_heap();
    if (VerifyArchivedFields > 0) {
      verify_loaded_heap();
    }
  }
  if (is_in_use()) {
    patch_native_pointers();
    intptr_t bottom = is_loaded() ? _loaded_heap_bottom : _mapped_heap_bottom;

    // The heap roots are stored in one or more segments that are laid out consecutively.
    // The size of each segment (except for the last one) is max_size_in_{elems,bytes}.
    HeapRootSegments segments = FileMapInfo::current_info()->mapped_heap()->root_segments();
    init_root_segment_sizes(segments.max_size_in_elems());
    intptr_t first_segment_addr = bottom + segments.base_offset();
    for (size_t c = 0; c < segments.count(); c++) {
      oop segment_oop = cast_to_oop(first_segment_addr + (c * segments.max_size_in_bytes()));
      assert(segment_oop->is_objArray(), "Must be");
      add_root_segment((objArrayOop)segment_oop);
    }

    if (CDSConfig::is_dumping_final_static_archive()) {
      StringTable::move_shared_strings_into_runtime_table();
    }
  }
}

void AOTMappedHeapLoader::finish_loaded_heap() {
  HeapWord* bottom = (HeapWord*)_loaded_heap_bottom;
  HeapWord* top    = (HeapWord*)_loaded_heap_top;

  MemRegion archive_space = MemRegion(bottom, top);
  Universe::heap()->complete_loaded_archive_space(archive_space);
}

void AOTMappedHeapLoader::verify_loaded_heap() {
  log_info(aot, heap)("Verify all oops and pointers in loaded heap");

  ResourceMark rm;
  HashTable<uintptr_t, bool> table;
  VerifyLoadedHeapEmbeddedPointers verifier(&table);
  HeapWord* bottom = (HeapWord*)_loaded_heap_bottom;
  HeapWord* top    = (HeapWord*)_loaded_heap_top;

  for (HeapWord* p = bottom; p < top; ) {
    oop o = cast_to_oop(p);
    table.put(cast_from_oop<uintptr_t>(o), true);
    p += o->size();
  }

  for (HeapWord* p = bottom; p < top; ) {
    oop o = cast_to_oop(p);
    o->oop_iterate(&verifier);
    p += o->size();
  }
}

void AOTMappedHeapLoader::fill_failed_loaded_heap() {
  assert(_loading_failed, "must be");
  if (_loaded_heap_bottom != 0) {
    assert(_loaded_heap_top != 0, "must be");
    HeapWord* bottom = (HeapWord*)_loaded_heap_bottom;
    HeapWord* top = (HeapWord*)_loaded_heap_top;
    Universe::heap()->fill_with_objects(bottom, top - bottom);
  }
}

class PatchNativePointers: public BitMapClosure {
  Metadata** _start;

 public:
  PatchNativePointers(Metadata** start) : _start(start) {}

  bool do_bit(size_t offset) {
    Metadata** p = _start + offset;
    *p = (Metadata*)(address(*p) + AOTMetaspace::relocation_delta());
    return true;
  }
};

void AOTMappedHeapLoader::patch_native_pointers() {
  if (AOTMetaspace::relocation_delta() == 0) {
    return;
  }

  FileMapRegion* r = FileMapInfo::current_info()->region_at(AOTMetaspace::hp);
  if (r->mapped_base() != nullptr && r->has_ptrmap()) {
    log_info(aot, heap)("Patching native pointers in heap region");
    BitMapView bm = FileMapInfo::current_info()->ptrmap_view(AOTMetaspace::hp);
    PatchNativePointers patcher((Metadata**)r->mapped_base() + FileMapInfo::current_info()->mapped_heap()->ptrmap_start_pos());
    bm.iterate(&patcher);
  }
}

// The actual address of this region during dump time.
address AOTMappedHeapLoader::heap_region_dumptime_address(FileMapInfo* info) {
  FileMapRegion* r = info->region_at(AOTMetaspace::hp);
  assert(CDSConfig::is_using_archive(), "runtime only");
  assert(is_aligned(r->mapping_offset(), sizeof(HeapWord)), "must be");
  if (UseCompressedOops) {
    return /*dumptime*/ (address)((uintptr_t)info->narrow_oop_base() + r->mapping_offset());
  } else {
    return heap_region_requested_address(info);
  }
}

// The address where this region can be mapped into the runtime heap without
// patching any of the pointers that are embedded in this region.
address AOTMappedHeapLoader::heap_region_requested_address(FileMapInfo* info) {
  assert(CDSConfig::is_using_archive(), "runtime only");
  FileMapRegion* r = info->region_at(AOTMetaspace::hp);
  assert(is_aligned(r->mapping_offset(), sizeof(HeapWord)), "must be");
  assert(can_use(), "cannot be used by AOTMappedHeapLoader::can_load() mode");
  if (UseCompressedOops) {
    // We can avoid relocation if each region's offset from the runtime CompressedOops::base()
    // is the same as its offset from the CompressedOops::base() during dumptime.
    // Note that CompressedOops::base() may be different between dumptime and runtime.
    //
    // Example:
    // Dumptime base = 0x1000 and shift is 0. We have a region at address 0x2000. There's a
    // narrowOop P stored in this region that points to an object at address 0x2200.
    // P's encoded value is 0x1200.
    //
    // Runtime base = 0x4000 and shift is also 0. If we map this region at 0x5000, then
    // the value P can remain 0x1200. The decoded address = (0x4000 + (0x1200 << 0)) = 0x5200,
    // which is the runtime location of the referenced object.
    return /*runtime*/ (address)((uintptr_t)CompressedOops::base() + r->mapping_offset());
  } else {
    // This was the hard-coded requested base address used at dump time. With uncompressed oops,
    // the heap range is assigned by the OS so we will most likely have to relocate anyway, no matter
    // what base address was picked at duump time.
    return (address)AOTMappedHeapWriter::NOCOOPS_REQUESTED_BASE;
  }
}

bool AOTMappedHeapLoader::map_heap_region(FileMapInfo* info) {
  if (map_heap_region_impl(info)) {
#ifdef ASSERT
    // The "old" regions must be parsable -- we cannot have any unused space
    // at the start of the lowest G1 region that contains archived objects.
    assert(is_aligned(_mapped_heap_memregion.start(), G1HeapRegion::GrainBytes), "must be");

    // Make sure we map at the very top of the heap - see comments in
    // init_heap_region_relocation().
    MemRegion heap_range = G1CollectedHeap::heap()->reserved();
    assert(heap_range.contains(_mapped_heap_memregion), "must be");

    address heap_end = (address)heap_range.end();
    address mapped_heap_region_end = (address)_mapped_heap_memregion.end();
    assert(heap_end >= mapped_heap_region_end, "must be");
    assert(heap_end - mapped_heap_region_end < (intx)(G1HeapRegion::GrainBytes),
           "must be at the top of the heap to avoid fragmentation");
#endif

    set_mapped();
    return true;
  } else {
    return false;
  }
}

bool AOTMappedHeapLoader::map_heap_region_impl(FileMapInfo* info) {
  assert(UseG1GC, "the following code assumes G1");

  FileMapRegion* r = info->region_at(AOTMetaspace::hp);
  size_t size = r->used();
  if (size == 0) {
    return false; // no archived java heap data
  }

  size_t word_size = size / HeapWordSize;
  address requested_start = heap_region_requested_address(info);

  aot_log_info(aot)("Preferred address to map heap data (to avoid relocation) is " INTPTR_FORMAT, p2i(requested_start));

  // allocate from java heap
  HeapWord* start = G1CollectedHeap::heap()->alloc_archive_region(word_size);
  if (start == nullptr) {
    AOTMetaspace::report_loading_error("UseSharedSpaces: Unable to allocate java heap region for archive heap.");
    return false;
  }

  _mapped_heap_memregion = MemRegion(start, word_size);

  // Map the archived heap data. No need to call MemTracker::record_virtual_memory_tag()
  // for mapped region as it is part of the reserved java heap, which is already recorded.
  char* addr = (char*)_mapped_heap_memregion.start();
  char* base;

  if (AOTMetaspace::use_windows_memory_mapping() || UseLargePages) {
    // With UseLargePages, memory mapping may fail on some OSes if the size is not
    // large page aligned, so let's use read() instead. In this case, the memory region
    // is already commited by G1 so we don't need to commit it again.
    if (!info->read_region(AOTMetaspace::hp, addr,
                           align_up(_mapped_heap_memregion.byte_size(), os::vm_page_size()),
                           /* do_commit = */ !UseLargePages)) {
      dealloc_heap_region(info);
      aot_log_error(aot)("Failed to read archived heap region into " INTPTR_FORMAT, p2i(addr));
      return false;
    }
    // Checks for VerifySharedSpaces is already done inside read_region()
    base = addr;
  } else {
    base = info->map_heap_region(r, addr, _mapped_heap_memregion.byte_size());
    if (base == nullptr || base != addr) {
      dealloc_heap_region(info);
      AOTMetaspace::report_loading_error("UseSharedSpaces: Unable to map at required address in java heap. "
                                            INTPTR_FORMAT ", size = %zu bytes",
                                            p2i(addr), _mapped_heap_memregion.byte_size());
      return false;
    }

    if (VerifySharedSpaces && !r->check_region_crc(base)) {
      dealloc_heap_region(info);
      AOTMetaspace::report_loading_error("UseSharedSpaces: mapped heap region is corrupt");
      return false;
    }
  }

  r->set_mapped_base(base);

  // If the requested range is different from the range allocated by GC, then
  // the pointers need to be patched.
  address mapped_start = (address) _mapped_heap_memregion.start();
  ptrdiff_t delta = mapped_start - requested_start;
  if (UseCompressedOops &&
      (info->narrow_oop_mode() != CompressedOops::mode() ||
       info->narrow_oop_shift() != CompressedOops::shift())) {
    _heap_pointers_need_patching = true;
  }
  if (delta != 0) {
    _heap_pointers_need_patching = true;
  }
  init_mapped_heap_info(mapped_start, delta, info->narrow_oop_shift());

  if (_heap_pointers_need_patching) {
    char* bitmap_base = info->map_bitmap_region();
    if (bitmap_base == nullptr) {
      AOTMetaspace::report_loading_error("CDS heap cannot be used because bitmap region cannot be mapped");
      dealloc_heap_region(info);
      _heap_pointers_need_patching = false;
      return false;
    }
  }
  aot_log_info(aot)("Heap data mapped at " INTPTR_FORMAT ", size = %8zu bytes",
                p2i(mapped_start), _mapped_heap_memregion.byte_size());
  aot_log_info(aot)("CDS heap data relocation delta = %zd bytes", delta);
  return true;
}

narrowOop AOTMappedHeapLoader::encoded_heap_region_dumptime_address(FileMapInfo* info) {
  assert(CDSConfig::is_using_archive(), "runtime only");
  assert(UseCompressedOops, "sanity");
  FileMapRegion* r = info->region_at(AOTMetaspace::hp);
  return CompressedOops::narrow_oop_cast(r->mapping_offset() >> info->narrow_oop_shift());
}

void AOTMappedHeapLoader::patch_heap_embedded_pointers(FileMapInfo* info) {
  if (!info->is_mapped() || !_heap_pointers_need_patching) {
    return;
  }

  char* bitmap_base = info->map_bitmap_region();
  assert(bitmap_base != nullptr, "must have already been mapped");

  FileMapRegion* r = info->region_at(AOTMetaspace::hp);
  patch_embedded_pointers(
      info, _mapped_heap_memregion,
      (address)(info->region_at(AOTMetaspace::bm)->mapped_base()) + r->oopmap_offset(),
      r->oopmap_size_in_bits());
}

void AOTMappedHeapLoader::fixup_mapped_heap_region(FileMapInfo* info) {
  if (is_mapped()) {
    assert(!_mapped_heap_memregion.is_empty(), "sanity");

    // Populate the archive regions' G1BlockOffsetTables. That ensures
    // fast G1BlockOffsetTable::block_start operations for any given address
    // within the archive regions when trying to find start of an object
    // (e.g. during card table scanning).
    G1CollectedHeap::heap()->populate_archive_regions_bot(_mapped_heap_memregion);
  }
}

// dealloc the archive regions from java heap
void AOTMappedHeapLoader::dealloc_heap_region(FileMapInfo* info) {
  G1CollectedHeap::heap()->dealloc_archive_regions(_mapped_heap_memregion);
}

AOTMapLogger::OopDataIterator* AOTMappedHeapLoader::oop_iterator(FileMapInfo* info, address buffer_start, address buffer_end) {
  class MappedLoaderOopIterator : public AOTMappedHeapOopIterator {
  public:
    MappedLoaderOopIterator(address buffer_start,
                            address buffer_end,
                            address requested_base,
                            address requested_start,
                            int requested_shift,
                            size_t num_root_segments) :
      AOTMappedHeapOopIterator(buffer_start,
                               buffer_end,
                               requested_base,
                               requested_start,
                               requested_shift,
                               num_root_segments) {}

    AOTMapLogger::OopData capture(address buffered_addr) override {
      oopDesc* raw_oop = (oopDesc*)buffered_addr;
      size_t size = raw_oop->size();
      address requested_addr = buffered_addr + _buffer_to_requested_delta;
      intptr_t target_location = intptr_t(requested_addr);
      uint64_t pd = (uint64_t)(pointer_delta(buffered_addr, _buffer_start, 1));
      uint32_t narrow_location = checked_cast<uint32_t>(_buffer_start_narrow_oop + (pd >> _requested_shift));
      Klass* klass = raw_oop->klass();

      return { buffered_addr,
               requested_addr,
               target_location,
               narrow_location,
               raw_oop,
               klass,
               size,
               false };
    }
  };

  FileMapRegion* r = info->region_at(AOTMetaspace::hp);
  address requested_base = UseCompressedOops ? (address)info->narrow_oop_base() : heap_region_requested_address(info);
  address requested_start = requested_base + r->mapping_offset();
  int requested_shift = info->narrow_oop_shift();

  return new MappedLoaderOopIterator(buffer_start,
                                     buffer_end,
                                     requested_base,
                                     requested_start,
                                     requested_shift,
                                     info->mapped_heap()->root_segments().count());
}

#endif // INCLUDE_CDS_JAVA_HEAP
