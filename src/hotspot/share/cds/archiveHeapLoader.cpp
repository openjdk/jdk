/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/archiveHeapLoader.inline.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "cds/metaspaceShared.hpp"
#include "classfile/classLoaderDataShared.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "logging/log.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/copy.hpp"

#if INCLUDE_CDS_JAVA_HEAP

bool ArchiveHeapLoader::_is_mapped = false;
bool ArchiveHeapLoader::_is_loaded = false;

bool    ArchiveHeapLoader::_narrow_oop_base_initialized = false;
address ArchiveHeapLoader::_narrow_oop_base;
int     ArchiveHeapLoader::_narrow_oop_shift;

// Support for loaded heap.
uintptr_t ArchiveHeapLoader::_loaded_heap_bottom = 0;
uintptr_t ArchiveHeapLoader::_loaded_heap_top = 0;
uintptr_t ArchiveHeapLoader::_dumptime_base = UINTPTR_MAX;
uintptr_t ArchiveHeapLoader::_dumptime_top = 0;
intx ArchiveHeapLoader::_runtime_offset = 0;
bool ArchiveHeapLoader::_loading_failed = false;

// Support for mapped heap.
uintptr_t ArchiveHeapLoader::_mapped_heap_bottom = 0;
bool      ArchiveHeapLoader::_mapped_heap_relocation_initialized = false;
ptrdiff_t ArchiveHeapLoader::_mapped_heap_delta = 0;

// Every mapped region is offset by _mapped_heap_delta from its requested address.
// See FileMapInfo::heap_region_requested_address().
#if defined(__clang__) || defined(__GNUC__)
__attribute__((no_sanitize("undefined")))
#endif
void ArchiveHeapLoader::init_mapped_heap_info(address mapped_heap_bottom, ptrdiff_t delta, int dumptime_oop_shift) {
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

void ArchiveHeapLoader::init_narrow_oop_decoding(address base, int shift) {
  assert(!_narrow_oop_base_initialized, "only once");
  _narrow_oop_base_initialized = true;
  _narrow_oop_base = base;
  _narrow_oop_shift = shift;
}

void ArchiveHeapLoader::fixup_region() {
  FileMapInfo* mapinfo = FileMapInfo::current_info();
  if (is_mapped()) {
    mapinfo->fixup_mapped_heap_region();
  } else if (_loading_failed) {
    fill_failed_loaded_heap();
  }
  if (is_in_use()) {
    if (!CDSConfig::is_using_full_module_graph()) {
      // Need to remove all the archived java.lang.Module objects from HeapShared::roots().
      ClassLoaderDataShared::clear_archived_oops();
    }
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
    oop o = ArchiveHeapLoader::decode_from_mapped_archive(v);
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
    oop o1 = ArchiveHeapLoader::decode_from_mapped_archive(v);
    oop o2 = CompressedOops::decode_not_null(new_v);
    assert(o1 == o2, "quick delta must work");
#endif
    RawAccess<IS_NOT_NULL>::oop_store(p, new_v);
    return true;
  }
};

class PatchUncompressedEmbeddedPointers: public BitMapClosure {
  oop* _start;

 public:
  PatchUncompressedEmbeddedPointers(oop* start) : _start(start) {}

  bool do_bit(size_t offset) {
    oop* p = _start + offset;
    intptr_t dumptime_oop = (intptr_t)((void*)*p);
    assert(dumptime_oop != 0, "null oops should have been filtered out at dump time");
    intptr_t runtime_oop = dumptime_oop + ArchiveHeapLoader::mapped_heap_delta();
    RawAccess<IS_NOT_NULL>::oop_store(p, cast_to_oop(runtime_oop));
    return true;
  }
};

void ArchiveHeapLoader::patch_compressed_embedded_pointers(BitMapView bm,
                                                  FileMapInfo* info,
                                                  MemRegion region) {
  narrowOop dt_encoded_bottom = info->encoded_heap_region_dumptime_address();
  narrowOop rt_encoded_bottom = CompressedOops::encode_not_null(cast_to_oop(region.start()));
  log_info(cds)("patching heap embedded pointers: narrowOop 0x%8x -> 0x%8x",
                  (uint)dt_encoded_bottom, (uint)rt_encoded_bottom);

  // Optimization: if dumptime shift is the same as runtime shift, we can perform a
  // quick conversion from "dumptime narrowOop" -> "runtime narrowOop".
  narrowOop* patching_start = (narrowOop*)region.start() + FileMapInfo::current_info()->heap_oopmap_start_pos();
  if (_narrow_oop_shift == CompressedOops::shift()) {
    uint32_t quick_delta = (uint32_t)rt_encoded_bottom - (uint32_t)dt_encoded_bottom;
    log_info(cds)("CDS heap data relocation quick delta = 0x%x", quick_delta);
    if (quick_delta == 0) {
      log_info(cds)("CDS heap data relocation unnecessary, quick_delta = 0");
    } else {
      PatchCompressedEmbeddedPointersQuick patcher(patching_start, quick_delta);
      bm.iterate(&patcher);
    }
  } else {
    log_info(cds)("CDS heap data quick relocation not possible");
    PatchCompressedEmbeddedPointers patcher(patching_start);
    bm.iterate(&patcher);
  }
}

// Patch all the non-null pointers that are embedded in the archived heap objects
// in this (mapped) region
void ArchiveHeapLoader::patch_embedded_pointers(FileMapInfo* info,
                                                MemRegion region, address oopmap,
                                                size_t oopmap_size_in_bits) {
  BitMapView bm((BitMap::bm_word_t*)oopmap, oopmap_size_in_bits);
  if (UseCompressedOops) {
    patch_compressed_embedded_pointers(bm, info, region);
  } else {
    PatchUncompressedEmbeddedPointers patcher((oop*)region.start() + FileMapInfo::current_info()->heap_oopmap_start_pos());
    bm.iterate(&patcher);
  }
}

// ------------------ Support for Region LOADING -----------------------------------------

// The CDS archive remembers each heap object by its address at dump time, but
// the heap object may be loaded at a different address at run time. This structure is used
// to translate the dump time addresses for all objects in FileMapInfo::space_at(region_index)
// to their runtime addresses.
struct LoadedArchiveHeapRegion {
  int       _region_index;   // index for FileMapInfo::space_at(index)
  size_t    _region_size;    // number of bytes in this region
  uintptr_t _dumptime_base;  // The dump-time (decoded) address of the first object in this region
  intx      _runtime_offset; // If an object's dump time address P is within in this region, its
                             // runtime address is P + _runtime_offset
  uintptr_t top() {
    return _dumptime_base + _region_size;
  }
};

void ArchiveHeapLoader::init_loaded_heap_relocation(LoadedArchiveHeapRegion* loaded_region) {
  _dumptime_base = loaded_region->_dumptime_base;
  _dumptime_top = loaded_region->top();
  _runtime_offset = loaded_region->_runtime_offset;
}

bool ArchiveHeapLoader::can_load() {
  if (!UseCompressedOops) {
    // Pointer relocation for uncompressed oops is unimplemented.
    return false;
  }
  return Universe::heap()->can_load_archived_objects();
}

class ArchiveHeapLoader::PatchLoadedRegionPointers: public BitMapClosure {
  narrowOop* _start;
  intx _offset;
  uintptr_t _base;
  uintptr_t _top;

 public:
  PatchLoadedRegionPointers(narrowOop* start, LoadedArchiveHeapRegion* loaded_region)
    : _start(start),
      _offset(loaded_region->_runtime_offset),
      _base(loaded_region->_dumptime_base),
      _top(loaded_region->top()) {}

  bool do_bit(size_t offset) {
    assert(UseCompressedOops, "PatchLoadedRegionPointers for uncompressed oops is unimplemented");
    narrowOop* p = _start + offset;
    narrowOop v = *p;
    assert(!CompressedOops::is_null(v), "null oops should have been filtered out at dump time");
    uintptr_t o = cast_from_oop<uintptr_t>(ArchiveHeapLoader::decode_from_archive(v));
    assert(_base <= o && o < _top, "must be");

    o += _offset;
    ArchiveHeapLoader::assert_in_loaded_heap(o);
    RawAccess<IS_NOT_NULL>::oop_store(p, cast_to_oop(o));
    return true;
  }
};

bool ArchiveHeapLoader::init_loaded_region(FileMapInfo* mapinfo, LoadedArchiveHeapRegion* loaded_region,
                                           MemRegion& archive_space) {
  size_t total_bytes = 0;
  FileMapRegion* r = mapinfo->region_at(MetaspaceShared::hp);
  r->assert_is_heap_region();
  if (r->used() == 0) {
    return false;
  }

  assert(is_aligned(r->used(), HeapWordSize), "must be");
  total_bytes += r->used();
  loaded_region->_region_index = MetaspaceShared::hp;
  loaded_region->_region_size = r->used();
  loaded_region->_dumptime_base = (uintptr_t)mapinfo->heap_region_dumptime_address();

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

bool ArchiveHeapLoader::load_heap_region_impl(FileMapInfo* mapinfo, LoadedArchiveHeapRegion* loaded_region,
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
    log_warning(cds)("Loading of heap region %d has failed. Archived objects are disabled", loaded_region->_region_index);
    _loading_failed = true;
    return false;
  }
  assert(r->mapped_base() == (char*)load_address, "sanity");
  log_info(cds)("Loaded heap    region #%d at base " INTPTR_FORMAT " top " INTPTR_FORMAT
                " size " SIZE_FORMAT_W(6) " delta " INTX_FORMAT,
                loaded_region->_region_index, load_address, load_address + loaded_region->_region_size,
                loaded_region->_region_size, loaded_region->_runtime_offset);

  uintptr_t oopmap = bitmap_base + r->oopmap_offset();
  BitMapView bm((BitMap::bm_word_t*)oopmap, r->oopmap_size_in_bits());

  PatchLoadedRegionPointers patcher((narrowOop*)load_address + FileMapInfo::current_info()->heap_oopmap_start_pos(), loaded_region);
  bm.iterate(&patcher);
  return true;
}

bool ArchiveHeapLoader::load_heap_region(FileMapInfo* mapinfo) {
  assert(UseCompressedOops, "loaded heap for !UseCompressedOops is unimplemented");
  init_narrow_oop_decoding(mapinfo->narrow_oop_base(), mapinfo->narrow_oop_shift());

  LoadedArchiveHeapRegion loaded_region;
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

class VerifyLoadedHeapEmbeddedPointers: public BasicOopIterateClosure {
  ResourceHashtable<uintptr_t, bool>* _table;

 public:
  VerifyLoadedHeapEmbeddedPointers(ResourceHashtable<uintptr_t, bool>* table) : _table(table) {}

  virtual void do_oop(narrowOop* p) {
    // This should be called before the loaded region is modified, so all the embedded pointers
    // must be null, or must point to a valid object in the loaded region.
    narrowOop v = *p;
    if (!CompressedOops::is_null(v)) {
      oop o = CompressedOops::decode_not_null(v);
      uintptr_t u = cast_from_oop<uintptr_t>(o);
      ArchiveHeapLoader::assert_in_loaded_heap(u);
      guarantee(_table->contains(u), "must point to beginning of object in loaded archived region");
    }
  }
  virtual void do_oop(oop* p) {
    // Uncompressed oops are not supported by loaded heaps.
    Unimplemented();
  }
};

void ArchiveHeapLoader::finish_initialization() {
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
    intptr_t roots_oop = bottom + FileMapInfo::current_info()->heap_roots_offset();
    HeapShared::init_roots(cast_to_oop(roots_oop));
  }
}

void ArchiveHeapLoader::finish_loaded_heap() {
  HeapWord* bottom = (HeapWord*)_loaded_heap_bottom;
  HeapWord* top    = (HeapWord*)_loaded_heap_top;

  MemRegion archive_space = MemRegion(bottom, top);
  Universe::heap()->complete_loaded_archive_space(archive_space);
}

void ArchiveHeapLoader::verify_loaded_heap() {
  log_info(cds, heap)("Verify all oops and pointers in loaded heap");

  ResourceMark rm;
  ResourceHashtable<uintptr_t, bool> table;
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

void ArchiveHeapLoader::fill_failed_loaded_heap() {
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
    *p = (Metadata*)(address(*p) + MetaspaceShared::relocation_delta());
    // Currently we have only Klass pointers in heap objects.
    // This needs to be relaxed when we support other types of native
    // pointers such as Method.
    assert(((Klass*)(*p))->is_klass(), "must be");
    return true;
  }
};

void ArchiveHeapLoader::patch_native_pointers() {
  if (MetaspaceShared::relocation_delta() == 0) {
    return;
  }

  FileMapRegion* r = FileMapInfo::current_info()->region_at(MetaspaceShared::hp);
  if (r->mapped_base() != nullptr && r->has_ptrmap()) {
    log_info(cds, heap)("Patching native pointers in heap region");
    BitMapView bm = FileMapInfo::current_info()->ptrmap_view(MetaspaceShared::hp);
    PatchNativePointers patcher((Metadata**)r->mapped_base() + FileMapInfo::current_info()->heap_ptrmap_start_pos());
    bm.iterate(&patcher);
  }
}
#endif // INCLUDE_CDS_JAVA_HEAP
