/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/filemap.hpp"
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

bool ArchiveHeapLoader::_closed_regions_mapped = false;
bool ArchiveHeapLoader::_open_regions_mapped = false;
bool ArchiveHeapLoader::_is_loaded = false;
address ArchiveHeapLoader::_narrow_oop_base;
int     ArchiveHeapLoader::_narrow_oop_shift;

// Support for loaded heap.
uintptr_t ArchiveHeapLoader::_loaded_heap_bottom = 0;
uintptr_t ArchiveHeapLoader::_loaded_heap_top = 0;
uintptr_t ArchiveHeapLoader::_dumptime_base_0 = UINTPTR_MAX;
uintptr_t ArchiveHeapLoader::_dumptime_base_1 = UINTPTR_MAX;
uintptr_t ArchiveHeapLoader::_dumptime_base_2 = UINTPTR_MAX;
uintptr_t ArchiveHeapLoader::_dumptime_base_3 = UINTPTR_MAX;
uintptr_t ArchiveHeapLoader::_dumptime_top    = 0;
intx ArchiveHeapLoader::_runtime_offset_0 = 0;
intx ArchiveHeapLoader::_runtime_offset_1 = 0;
intx ArchiveHeapLoader::_runtime_offset_2 = 0;
intx ArchiveHeapLoader::_runtime_offset_3 = 0;
bool ArchiveHeapLoader::_loading_failed = false;

void ArchiveHeapLoader::init_narrow_oop_decoding(address base, int shift) {
  _narrow_oop_base = base;
  _narrow_oop_shift = shift;
}

void ArchiveHeapLoader::fixup_regions() {
  FileMapInfo* mapinfo = FileMapInfo::current_info();
  if (is_mapped()) {
    fill_failed_mapped_regions();
  } else if (_loading_failed) {
    fill_failed_loaded_heap();
  }
  if (is_fully_available()) {
    if (!MetaspaceShared::use_full_module_graph()) {
      // Need to remove all the archived java.lang.Module objects from HeapShared::roots().
      ClassLoaderDataShared::clear_archived_oops();
    }
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

  static int comparator(const void* a, const void* b) {
    LoadedArchiveHeapRegion* reg_a = (LoadedArchiveHeapRegion*)a;
    LoadedArchiveHeapRegion* reg_b = (LoadedArchiveHeapRegion*)b;
    if (reg_a->_dumptime_base < reg_b->_dumptime_base) {
      return -1;
    } else if (reg_a->_dumptime_base == reg_b->_dumptime_base) {
      return 0;
    } else {
      return 1;
    }
  }

  uintptr_t top() {
    return _dumptime_base + _region_size;
  }
};

void ArchiveHeapLoader::init_loaded_heap_relocation(LoadedArchiveHeapRegion* loaded_regions,
                                                    int num_loaded_regions) {
  _dumptime_base_0 = loaded_regions[0]._dumptime_base;
  _dumptime_base_1 = loaded_regions[1]._dumptime_base;
  _dumptime_base_2 = loaded_regions[2]._dumptime_base;
  _dumptime_base_3 = loaded_regions[3]._dumptime_base;
  _dumptime_top = loaded_regions[num_loaded_regions-1].top();

  _runtime_offset_0 = loaded_regions[0]._runtime_offset;
  _runtime_offset_1 = loaded_regions[1]._runtime_offset;
  _runtime_offset_2 = loaded_regions[2]._runtime_offset;
  _runtime_offset_3 = loaded_regions[3]._runtime_offset;

  assert(2 <= num_loaded_regions && num_loaded_regions <= 4, "must be");
  if (num_loaded_regions < 4) {
    _dumptime_base_3 = UINTPTR_MAX;
  }
  if (num_loaded_regions < 3) {
    _dumptime_base_2 = UINTPTR_MAX;
  }
}

bool ArchiveHeapLoader::can_load() {
  return Universe::heap()->can_load_archived_objects();
}

template <int NUM_LOADED_REGIONS>
class PatchLoadedRegionPointers: public BitMapClosure {
  narrowOop* _start;
  intx _offset_0;
  intx _offset_1;
  intx _offset_2;
  intx _offset_3;
  uintptr_t _base_0;
  uintptr_t _base_1;
  uintptr_t _base_2;
  uintptr_t _base_3;
  uintptr_t _top;

  static_assert(MetaspaceShared::max_num_heap_regions == 4, "can't handle more than 4 regions");
  static_assert(NUM_LOADED_REGIONS >= 2, "we have at least 2 loaded regions");
  static_assert(NUM_LOADED_REGIONS <= 4, "we have at most 4 loaded regions");

 public:
  PatchLoadedRegionPointers(narrowOop* start, LoadedArchiveHeapRegion* loaded_regions)
    : _start(start),
      _offset_0(loaded_regions[0]._runtime_offset),
      _offset_1(loaded_regions[1]._runtime_offset),
      _offset_2(loaded_regions[2]._runtime_offset),
      _offset_3(loaded_regions[3]._runtime_offset),
      _base_0(loaded_regions[0]._dumptime_base),
      _base_1(loaded_regions[1]._dumptime_base),
      _base_2(loaded_regions[2]._dumptime_base),
      _base_3(loaded_regions[3]._dumptime_base) {
    _top = loaded_regions[NUM_LOADED_REGIONS-1].top();
  }

  bool do_bit(size_t offset) {
    narrowOop* p = _start + offset;
    narrowOop v = *p;
    assert(!CompressedOops::is_null(v), "null oops should have been filtered out at dump time");
    uintptr_t o = cast_from_oop<uintptr_t>(ArchiveHeapLoader::decode_from_archive(v));
    assert(_base_0 <= o && o < _top, "must be");


    // We usually have only 2 regions for the default archive. Use template to avoid unnecessary comparisons.
    if (NUM_LOADED_REGIONS > 3 && o >= _base_3) {
      o += _offset_3;
    } else if (NUM_LOADED_REGIONS > 2 && o >= _base_2) {
      o += _offset_2;
    } else if (o >= _base_1) {
      o += _offset_1;
    } else {
      o += _offset_0;
    }
    ArchiveHeapLoader::assert_in_loaded_heap(o);
    RawAccess<IS_NOT_NULL>::oop_store(p, cast_to_oop(o));
    return true;
  }
};

int ArchiveHeapLoader::init_loaded_regions(FileMapInfo* mapinfo, LoadedArchiveHeapRegion* loaded_regions,
                                           MemRegion& archive_space) {
  size_t total_bytes = 0;
  int num_loaded_regions = 0;
  for (int i = MetaspaceShared::first_archive_heap_region;
       i <= MetaspaceShared::last_archive_heap_region; i++) {
    FileMapRegion* r = mapinfo->space_at(i);
    r->assert_is_heap_region();
    if (r->used() > 0) {
      assert(is_aligned(r->used(), HeapWordSize), "must be");
      total_bytes += r->used();
      LoadedArchiveHeapRegion* ri = &loaded_regions[num_loaded_regions++];
      ri->_region_index = i;
      ri->_region_size = r->used();
      ri->_dumptime_base = (uintptr_t)mapinfo->start_address_as_decoded_from_archive(r);
    }
  }

  assert(is_aligned(total_bytes, HeapWordSize), "must be");
  size_t word_size = total_bytes / HeapWordSize;
  HeapWord* buffer = Universe::heap()->allocate_loaded_archive_space(word_size);
  if (buffer == nullptr) {
    return 0;
  }

  archive_space = MemRegion(buffer, word_size);
  _loaded_heap_bottom = (uintptr_t)archive_space.start();
  _loaded_heap_top    = _loaded_heap_bottom + total_bytes;

  return num_loaded_regions;
}

void ArchiveHeapLoader::sort_loaded_regions(LoadedArchiveHeapRegion* loaded_regions, int num_loaded_regions,
                                            uintptr_t buffer) {
  // Find the relocation offset of the pointers in each region
  qsort(loaded_regions, num_loaded_regions, sizeof(LoadedArchiveHeapRegion),
        LoadedArchiveHeapRegion::comparator);

  uintptr_t p = buffer;
  for (int i = 0; i < num_loaded_regions; i++) {
    // This region will be loaded at p, so all objects inside this
    // region will be shifted by ri->offset
    LoadedArchiveHeapRegion* ri = &loaded_regions[i];
    ri->_runtime_offset = p - ri->_dumptime_base;
    p += ri->_region_size;
  }
  assert(p == _loaded_heap_top, "must be");
}

bool ArchiveHeapLoader::load_regions(FileMapInfo* mapinfo, LoadedArchiveHeapRegion* loaded_regions,
                                     int num_loaded_regions, uintptr_t buffer) {
  uintptr_t bitmap_base = (uintptr_t)mapinfo->map_bitmap_region();
  if (bitmap_base == 0) {
    _loading_failed = true;
    return false; // OOM or CRC error
  }
  uintptr_t load_address = buffer;
  for (int i = 0; i < num_loaded_regions; i++) {
    LoadedArchiveHeapRegion* ri = &loaded_regions[i];
    FileMapRegion* r = mapinfo->space_at(ri->_region_index);

    if (!mapinfo->read_region(ri->_region_index, (char*)load_address, r->used(), /* do_commit = */ false)) {
      // There's no easy way to free the buffer, so we will fill it with zero later
      // in fill_failed_loaded_heap(), and it will eventually be GC'ed.
      log_warning(cds)("Loading of heap region %d has failed. Archived objects are disabled", i);
      _loading_failed = true;
      return false;
    }
    log_info(cds)("Loaded heap    region #%d at base " INTPTR_FORMAT " top " INTPTR_FORMAT
                  " size " SIZE_FORMAT_W(6) " delta " INTX_FORMAT,
                  ri->_region_index, load_address, load_address + ri->_region_size,
                  ri->_region_size, ri->_runtime_offset);

    uintptr_t oopmap = bitmap_base + r->oopmap_offset();
    BitMapView bm((BitMap::bm_word_t*)oopmap, r->oopmap_size_in_bits());

    if (num_loaded_regions == 4) {
      PatchLoadedRegionPointers<4> patcher((narrowOop*)load_address, loaded_regions);
      bm.iterate(&patcher);
    } else if (num_loaded_regions == 3) {
      PatchLoadedRegionPointers<3> patcher((narrowOop*)load_address, loaded_regions);
      bm.iterate(&patcher);
    } else {
      assert(num_loaded_regions == 2, "must be");
      PatchLoadedRegionPointers<2> patcher((narrowOop*)load_address, loaded_regions);
      bm.iterate(&patcher);
    }

    r->set_mapped_base((char*)load_address);
    load_address += r->used();
  }

  return true;
}

bool ArchiveHeapLoader::load_heap_regions(FileMapInfo* mapinfo) {
  init_narrow_oop_decoding(mapinfo->narrow_oop_base(), mapinfo->narrow_oop_shift());

  LoadedArchiveHeapRegion loaded_regions[MetaspaceShared::max_num_heap_regions];
  memset(loaded_regions, 0, sizeof(loaded_regions));

  MemRegion archive_space;
  int num_loaded_regions = init_loaded_regions(mapinfo, loaded_regions, archive_space);
  if (num_loaded_regions <= 0) {
    return false;
  }
  sort_loaded_regions(loaded_regions, num_loaded_regions, (uintptr_t)archive_space.start());
  if (!load_regions(mapinfo, loaded_regions, num_loaded_regions, (uintptr_t)archive_space.start())) {
    assert(_loading_failed, "must be");
    return false;
  }

  init_loaded_heap_relocation(loaded_regions, num_loaded_regions);
  _is_loaded = true;

  return true;
}

class VerifyLoadedHeapEmbeddedPointers: public BasicOopIterateClosure {
  ResourceHashtable<uintptr_t, bool>* _table;

 public:
  VerifyLoadedHeapEmbeddedPointers(ResourceHashtable<uintptr_t, bool>* table) : _table(table) {}

  virtual void do_oop(narrowOop* p) {
    // This should be called before the loaded regions are modified, so all the embedded pointers
    // must be NULL, or must point to a valid object in the loaded regions.
    narrowOop v = *p;
    if (!CompressedOops::is_null(v)) {
      oop o = CompressedOops::decode_not_null(v);
      uintptr_t u = cast_from_oop<uintptr_t>(o);
      ArchiveHeapLoader::assert_in_loaded_heap(u);
      guarantee(_table->contains(u), "must point to beginning of object in loaded archived regions");
    }
  }
  virtual void do_oop(oop* p) {
    ShouldNotReachHere();
  }
};

void ArchiveHeapLoader::finish_initialization() {
  if (is_mapped()) {
    complete_heap_regions_mapping();
  }
  if (is_loaded()) {
    // These operations are needed only when the heap is loaded (not mapped).
    finish_loaded_heap();
    if (VerifyArchivedFields > 0) {
      verify_loaded_heap();
    }
  }
  patch_native_pointers();
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

  for (int i = MetaspaceShared::first_archive_heap_region;
       i <= MetaspaceShared::last_archive_heap_region; i++) {
    FileMapRegion* r = FileMapInfo::current_info()->space_at(i);
    if (r->mapped_base() != NULL && r->has_ptrmap()) {
      log_info(cds, heap)("Patching native pointers in heap region %d", i);
      BitMapView bm = r->ptrmap_view();
      PatchNativePointers patcher((Metadata**)r->mapped_base());
      bm.iterate(&patcher);
    }
  }
}

bool ArchiveHeapLoader::_heap_pointers_need_patching = false;
ArchiveHeapRegions ArchiveHeapLoader::_closed_heap_regions;
ArchiveHeapRegions ArchiveHeapLoader::_open_heap_regions;
ArchiveOopDecoder* ArchiveHeapLoader::_oop_decoder = NULL;

void ArchiveHeapLoader::init_archive_heap_regions(FileMapInfo* map_info, int first_region_idx, int last_region_idx, ArchiveHeapRegions* heap_regions) {
  heap_regions->init(last_region_idx - first_region_idx + 1);
  int count = 0;

  for (int i = first_region_idx; i <= last_region_idx; i++) {
    FileMapRegion* si = map_info->space_at(i);
    si->assert_is_heap_region();
    size_t size = si->used();
    if (size > 0) {
      HeapWord* start = (HeapWord*)map_info->start_address_at_dumptime(si);
      heap_regions->set_dumptime_region(count, MemRegion(start, size / HeapWordSize));
      heap_regions->set_region_index(count, i);
      count += 1;
    }
  }
  heap_regions->set_num_regions(count);
  return;
}

void ArchiveHeapLoader::cleanup_regions(FileMapInfo* map_info, ArchiveHeapRegions* heap_regions) {
  if (heap_regions->is_mapped()) {
    // unmap the regions ...
    for (int i = 0; i < heap_regions->num_regions(); i++) {
      int region_idx = heap_regions->region_index(i);
      assert(region_idx >= MetaspaceShared::first_archive_heap_region
             && region_idx <= MetaspaceShared::last_archive_heap_region,
             "invalid index");
      map_info->unmap_region(region_idx);
    }
    // .. and now change state to HEAP_RESERVED.
    heap_regions->set_state(ArchiveHeapRegions::HEAP_RESERVED);
  }
  if (heap_regions->is_runtime_space_reserved()) {
    if (dealloc_heap_regions(heap_regions)) {
      heap_regions->set_state(ArchiveHeapRegions::MAPPING_FAILED_DEALLOCATED);
    } else {
      // if we fail to dealloc, the regions will be filled up with dummy objects
      // later in ArchiveHeapLoader::fixup_regions to make them parseable
      heap_regions->set_state(ArchiveHeapRegions::MAPPING_FAILED);
    }
  }
}

void ArchiveHeapLoader::cleanup(FileMapInfo* map_info) {
  cleanup_regions(map_info, &_closed_heap_regions);
  cleanup_regions(map_info, &_open_heap_regions);
}

bool ArchiveHeapLoader::get_heap_range_for_archive_regions(ArchiveHeapRegions* heap_regions, bool is_open) {
  if (Universe::heap()->alloc_archive_regions(heap_regions->dumptime_regions(),
                                           heap_regions->num_regions(),
                                           heap_regions->runtime_regions(),
                                           is_open)) {
    heap_regions->set_state(ArchiveHeapRegions::HEAP_RESERVED);
    return true;
  }
  return false;
}

bool ArchiveHeapLoader::is_pointer_patching_needed(FileMapInfo* map_info) {
  if (!_closed_heap_regions.is_mapped()) {
    assert(!_open_heap_regions.is_mapped(), "open heap regions must not be mapped when closed heap regions are not mapped");
    return false;
  }
  if (_closed_heap_regions.is_relocated()) {
    log_info(cds)("CDS heap data needs to be relocated.");
    return true;
  }
  assert(_open_heap_regions.is_mapped(), "open heap regions must be mapped");
  if (_open_heap_regions.is_relocated()) {
    log_info(cds)("CDS heap data needs to be relocated.");
    return true;
  }
  if (map_info->narrow_oop_mode() != CompressedOops::mode() ||
      map_info->narrow_oop_base() != CompressedOops::base() ||
      map_info->narrow_oop_shift() != CompressedOops::shift()) {
    log_info(cds)("CDS heap data needs to be relocated because the archive was created with an incompatible oop encoding mode.");
    return true;
  }
  return false;
}

void ArchiveHeapLoader::log_mapped_regions(ArchiveHeapRegions* heap_regions, bool is_open) {
  if (is_open) {
    log_info(cds)("open heap regions:");
  } else {
    log_info(cds)("closed heap regions:");
  }
  for (int i = 0; i < heap_regions->num_regions(); i++) {
    log_info(cds)("dumptime region: [" PTR_FORMAT " - " PTR_FORMAT "] mapped to [" PTR_FORMAT " - " PTR_FORMAT "]",
                   p2i(heap_regions->dumptime_region(i).start()), p2i(heap_regions->dumptime_region(i).end()),
                   p2i(heap_regions->runtime_region(i).start()), p2i(heap_regions->runtime_region(i).end()));
  }
}

void ArchiveHeapLoader::map_heap_regions(FileMapInfo* map_info) {
  init_archive_heap_regions(map_info, MetaspaceShared::first_closed_heap_region,
                            MetaspaceShared::last_closed_heap_region, &_closed_heap_regions);
  if (!get_heap_range_for_archive_regions(&_closed_heap_regions, false)) {
    log_info(cds)("Failed to find free regions in the heap for closed heap archive space");
    cleanup(map_info);
    return;
  }

  init_archive_heap_regions(map_info, MetaspaceShared::first_open_heap_region,
                            MetaspaceShared::last_open_heap_region, &_open_heap_regions);
  if (!get_heap_range_for_archive_regions(&_open_heap_regions, true)) {
    log_info(cds)("Failed to find free regions for in the heap for open heap archive space");
    cleanup(map_info);
    return;
  }

  char* bitmap_base = map_info->map_bitmap_region();
  if (bitmap_base == NULL) {
    log_info(cds)("CDS heap cannot be used because bitmap region cannot be mapped");
    cleanup(map_info);
    return;
  }

  // Map the heap regions
  // closed regions: GC does not write into these regions.
  // open regions: GC can write into these regions.
  if (!map_heap_regions(map_info, &_closed_heap_regions) ||
      !map_heap_regions(map_info, &_open_heap_regions)) {
    cleanup(map_info);
    return;
  }

  _heap_pointers_need_patching = is_pointer_patching_needed(map_info);

  if (_closed_heap_regions.is_mapped()) {
    log_mapped_regions(&_closed_heap_regions, false);
  }
  if (_open_heap_regions.is_mapped()) {
    log_mapped_regions(&_open_heap_regions, true);
  }
}

bool ArchiveHeapLoader::map_heap_regions(FileMapInfo* map_info, ArchiveHeapRegions* heap_regions) {
  MemRegion* regions = heap_regions->runtime_regions();
  int num_regions = heap_regions->num_regions();

  assert(heap_regions->is_runtime_space_reserved(), "heap space for the archive heap regions must be reserved");
  for (int i = 0; i < num_regions; i++) {
    FileMapRegion* si = map_info->space_at(heap_regions->region_index(i));
    char* addr = (char*)regions[i].start();
    char* base = map_info->map_region_at_address(si, addr, regions[i].byte_size());
    if (base == NULL || base != addr) {
      log_info(cds)("UseSharedSpaces: Unable to map at required address in java heap. "
                    INTPTR_FORMAT ", size = " SIZE_FORMAT " bytes",
                    p2i(addr), regions[i].byte_size());
      cleanup_regions(map_info, heap_regions);
      return false;
    }

    si->set_mapped_base(base);
    heap_regions->set_state(ArchiveHeapRegions::MAPPED);

    if (VerifySharedSpaces && !map_info->region_crc_check(addr, regions[i].byte_size(), si->crc())) {
      log_info(cds)("UseSharedSpaces: mapped heap regions are corrupt");
      cleanup_regions(map_info, heap_regions);
      return false;
    }
  }

  return true;
}

void ArchiveHeapLoader::complete_heap_regions_mapping() {
  if (closed_regions_mapped()) {
    Universe::heap()->complete_archive_regions_alloc(_closed_heap_regions.runtime_regions(), _closed_heap_regions.num_regions());
  }
  if (open_regions_mapped()) {
    Universe::heap()->complete_archive_regions_alloc(_open_heap_regions.runtime_regions(), _open_heap_regions.num_regions());
  }
}

// dealloc the archive regions from java heap
bool ArchiveHeapLoader::dealloc_heap_regions(ArchiveHeapRegions* heap_regions) {
  return Universe::heap()->dealloc_archive_regions(heap_regions->runtime_regions(), heap_regions->num_regions());
}

ArchiveOopDecoder* ArchiveHeapLoader::get_oop_decoder(FileMapInfo* map_info) {
  if (closed_regions_mapped() && !_oop_decoder) {
    assert(open_regions_mapped(), "open heap regions must be mapped");
    if (UseCompressedOops) {
      _oop_decoder = new ArchiveNarrowOopDecoder(&_closed_heap_regions, &_open_heap_regions,
                                                 map_info->narrow_oop_base(), map_info->narrow_oop_shift());
    } else {
      _oop_decoder = new ArchiveWideOopDecoder(&_closed_heap_regions, &_open_heap_regions);
    }
  }
  return _oop_decoder;
}

// Patch all the embedded oop pointers inside an archived heap region,
// to be consistent with the runtime oop encoding.
template <typename T>
class PatchEmbeddedPointers: public BitMapClosure {
  T* _start;
  ArchiveOopDecoder* _oop_decoder;

 public:
  PatchEmbeddedPointers(T* start, ArchiveOopDecoder* oop_decoder) :
    _start(start),
    _oop_decoder(oop_decoder)
  {}

  bool do_bit(size_t offset) {
    T* p = _start + offset;
    uintptr_t dumptime_oop = (uintptr_t)((void *)*p);
    oop runtime_oop = _oop_decoder->decode(dumptime_oop);
    assert(runtime_oop != NULL, "null oops should have been filtered out at dump time");
    RawAccess<IS_NOT_NULL>::oop_store((T *)p, runtime_oop);
    return true;
  }
};

void ArchiveHeapLoader::patch_embedded_pointers(FileMapInfo* map_info, MemRegion region, address oopmap, size_t oopmap_size_in_bits) {
  BitMapView bm((BitMap::bm_word_t*)oopmap, oopmap_size_in_bits);
  ArchiveOopDecoder* oop_decoder = get_oop_decoder(map_info);

#ifndef PRODUCT
  ResourceMark rm;
  ResourceBitMap checkBm = HeapShared::calculate_oopmap(region);
  assert(bm.is_same(checkBm), "sanity");
#endif

  if (UseCompressedOops) {
    PatchEmbeddedPointers<narrowOop> patcher((narrowOop*)region.start(), oop_decoder);
    bm.iterate(&patcher);
  } else {
    PatchEmbeddedPointers<oop> patcher((oop*)region.start(), oop_decoder);
    bm.iterate(&patcher);
  }
}

void ArchiveHeapLoader::patch_heap_embedded_pointers(FileMapInfo* map_info, ArchiveHeapRegions* heap_regions) {
  char* bitmap_base = map_info->map_bitmap_region();
  assert(bitmap_base != NULL, "must have already been mapped");

  for (int i = 0; i < heap_regions->num_regions(); i++) {
    FileMapRegion* si = map_info->space_at(heap_regions->region_index(i));
    patch_embedded_pointers(map_info, heap_regions->runtime_region(i),
      (address)(bitmap_base) + si->oopmap_offset(),
      si->oopmap_size_in_bits());
  }
}

void ArchiveHeapLoader::patch_heap_embedded_pointers(FileMapInfo* map_info) {
  if (!_heap_pointers_need_patching) {
    return;
  }

  log_info(cds)("patching heap embedded pointers");

  assert(_closed_heap_regions.is_mapped(), "closed heap regions must have been successfully mapped");
  assert(_open_heap_regions.is_mapped(), "open regions must have been successfully mapped");
  patch_heap_embedded_pointers(map_info, &_closed_heap_regions);
  patch_heap_embedded_pointers(map_info, &_open_heap_regions);
}

bool ArchiveHeapLoader::is_archived_object(oop object) {
  if (_closed_heap_regions.is_mapped()) {
    if (_closed_heap_regions.is_in_runtime_region(cast_from_oop<uintptr_t>(object))) {
      return true;
    }
    assert(_open_heap_regions.is_mapped(), "open heap regions must be mapped");
    if (_open_heap_regions.is_in_runtime_region(cast_from_oop<uintptr_t>(object))) {
      return true;
    }
  } else {
    assert(!_open_heap_regions.is_mapped(), "open heap regions should not be mapped when closed heap regions are not mapped");
  }
  return false;
}

bool ArchiveHeapLoader::is_archived_object(oop object) {
  if (_closed_heap_regions.is_mapped()) {
    if (_closed_heap_regions.is_in_runtime_region(cast_from_oop<uintptr_t>(object))) {
      return true;
    }
    if (_open_heap_regions.is_mapped()) {
      if (_open_heap_regions.is_in_runtime_region(cast_from_oop<uintptr_t>(object))) {
        return true;
      }
    }
  } else {
    assert(!_open_heap_regions.is_mapped(), "open heap regions should not be mapped when closed heap regions are not mapped");
  }
  return false;
}

void ArchiveHeapLoader::fill_failed_mapped_regions() {
  if (_closed_heap_regions.is_mapping_failed()) {
    Universe::heap()->fill_heap_regions(_closed_heap_regions.runtime_regions(), _closed_heap_regions.num_regions());
  }
  if (_open_heap_regions.is_mapping_failed()) {
    Universe::heap()->fill_heap_regions(_open_heap_regions.runtime_regions(), _open_heap_regions.num_regions());
  }
}

#endif // INCLUDE_CDS_JAVA_HEAP
