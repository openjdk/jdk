/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotArtifactFinder.hpp"
#include "cds/aotClassLinker.hpp"
#include "cds/aotLogging.hpp"
#include "cds/aotMapLogger.hpp"
#include "cds/aotMetaspace.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/archiveUtils.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/cppVtables.hpp"
#include "cds/dumpAllocStats.hpp"
#include "cds/dynamicArchive.hpp"
#include "cds/heapShared.hpp"
#include "cds/regeneratedClasses.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderDataShared.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "code/aotCodeCache.hpp"
#include "interpreter/abstractInterpreter.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "memory/allStatic.hpp"
#include "memory/memoryReserver.hpp"
#include "memory/memRegion.hpp"
#include "memory/resourceArea.hpp"
#include "oops/compressedKlass.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/methodCounters.hpp"
#include "oops/methodData.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/trainingData.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/align.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/formatBuffer.hpp"

ArchiveBuilder* ArchiveBuilder::_current = nullptr;

ArchiveBuilder::OtherROAllocMark::~OtherROAllocMark() {
  char* newtop = ArchiveBuilder::current()->_ro_region.top();
  ArchiveBuilder::alloc_stats()->record_other_type(int(newtop - _oldtop), true);
}

ArchiveBuilder::SourceObjList::SourceObjList() : _ptrmap(16 * K, mtClassShared) {
  _total_bytes = 0;
  _objs = new (mtClassShared) GrowableArray<SourceObjInfo*>(128 * K, mtClassShared);
}

ArchiveBuilder::SourceObjList::~SourceObjList() {
  delete _objs;
}

void ArchiveBuilder::SourceObjList::append(SourceObjInfo* src_info) {
  // Save this source object for copying
  src_info->set_id(_objs->length());
  _objs->append(src_info);

  // Prepare for marking the pointers in this source object
  assert(is_aligned(_total_bytes, sizeof(address)), "must be");
  src_info->set_ptrmap_start(_total_bytes / sizeof(address));
  _total_bytes = align_up(_total_bytes + (uintx)src_info->size_in_bytes(), sizeof(address));
  src_info->set_ptrmap_end(_total_bytes / sizeof(address));

  BitMap::idx_t bitmap_size_needed = BitMap::idx_t(src_info->ptrmap_end());
  if (_ptrmap.size() <= bitmap_size_needed) {
    _ptrmap.resize((bitmap_size_needed + 1) * 2);
  }
}

void ArchiveBuilder::SourceObjList::remember_embedded_pointer(SourceObjInfo* src_info, MetaspaceClosure::Ref* ref) {
  // src_obj contains a pointer. Remember the location of this pointer in _ptrmap,
  // so that we can copy/relocate it later.
  src_info->set_has_embedded_pointer();
  address src_obj = src_info->source_addr();
  address* field_addr = ref->addr();
  assert(src_info->ptrmap_start() < _total_bytes, "sanity");
  assert(src_info->ptrmap_end() <= _total_bytes, "sanity");
  assert(*field_addr != nullptr, "should have checked");

  intx field_offset_in_bytes = ((address)field_addr) - src_obj;
  DEBUG_ONLY(int src_obj_size = src_info->size_in_bytes();)
  assert(field_offset_in_bytes >= 0, "must be");
  assert(field_offset_in_bytes + intx(sizeof(intptr_t)) <= intx(src_obj_size), "must be");
  assert(is_aligned(field_offset_in_bytes, sizeof(address)), "must be");

  BitMap::idx_t idx = BitMap::idx_t(src_info->ptrmap_start() + (uintx)(field_offset_in_bytes / sizeof(address)));
  _ptrmap.set_bit(BitMap::idx_t(idx));
}

class RelocateEmbeddedPointers : public BitMapClosure {
  ArchiveBuilder* _builder;
  address _buffered_obj;
  BitMap::idx_t _start_idx;
public:
  RelocateEmbeddedPointers(ArchiveBuilder* builder, address buffered_obj, BitMap::idx_t start_idx) :
    _builder(builder), _buffered_obj(buffered_obj), _start_idx(start_idx) {}

  bool do_bit(BitMap::idx_t bit_offset) {
    size_t field_offset = size_t(bit_offset - _start_idx) * sizeof(address);
    address* ptr_loc = (address*)(_buffered_obj + field_offset);

    address old_p_with_tags = *ptr_loc;
    assert(old_p_with_tags != nullptr, "null ptrs shouldn't have been marked");

    address old_p = MetaspaceClosure::strip_tags(old_p_with_tags);
    uintx tags = MetaspaceClosure::decode_tags(old_p_with_tags);
    address new_p = _builder->get_buffered_addr(old_p);

    bool nulled;
    if (new_p == nullptr) {
      // old_p had a FollowMode of set_to_null
      nulled = true;
    } else {
      new_p = MetaspaceClosure::add_tags(new_p, tags);
      nulled = false;
    }

    log_trace(aot)("Ref: [" PTR_FORMAT "] -> " PTR_FORMAT " => " PTR_FORMAT " %zu",
                   p2i(ptr_loc), p2i(old_p) + tags, p2i(new_p), tags);

    ArchivePtrMarker::set_and_mark_pointer(ptr_loc, new_p);
    ArchiveBuilder::current()->count_relocated_pointer(tags != 0, nulled);
    return true; // keep iterating the bitmap
  }
};

void ArchiveBuilder::SourceObjList::relocate(int i, ArchiveBuilder* builder) {
  SourceObjInfo* src_info = objs()->at(i);
  assert(src_info->should_copy(), "must be");
  BitMap::idx_t start = BitMap::idx_t(src_info->ptrmap_start()); // inclusive
  BitMap::idx_t end = BitMap::idx_t(src_info->ptrmap_end());     // exclusive

  RelocateEmbeddedPointers relocator(builder, src_info->buffered_addr(), start);
  _ptrmap.iterate(&relocator, start, end);
}

ArchiveBuilder::ArchiveBuilder() :
  _current_dump_region(nullptr),
  _buffer_bottom(nullptr),
  _requested_static_archive_bottom(nullptr),
  _requested_static_archive_top(nullptr),
  _requested_dynamic_archive_bottom(nullptr),
  _requested_dynamic_archive_top(nullptr),
  _mapped_static_archive_bottom(nullptr),
  _mapped_static_archive_top(nullptr),
  _buffer_to_requested_delta(0),
  _pz_region("pz", MAX_SHARED_DELTA), // protection zone -- used only during dumping; does NOT exist in cds archive.
  _rw_region("rw", MAX_SHARED_DELTA),
  _ro_region("ro", MAX_SHARED_DELTA),
  _ac_region("ac", MAX_SHARED_DELTA),
  _ptrmap(mtClassShared),
  _rw_ptrmap(mtClassShared),
  _ro_ptrmap(mtClassShared),
  _rw_src_objs(),
  _ro_src_objs(),
  _src_obj_table(INITIAL_TABLE_SIZE, MAX_TABLE_SIZE),
  _buffered_to_src_table(INITIAL_TABLE_SIZE, MAX_TABLE_SIZE),
  _total_heap_region_size(0)
{
  _klasses = new (mtClassShared) GrowableArray<Klass*>(4 * K, mtClassShared);
  _symbols = new (mtClassShared) GrowableArray<Symbol*>(256 * K, mtClassShared);
  _entropy_seed = 0x12345678;
  _relocated_ptr_info._num_ptrs = 0;
  _relocated_ptr_info._num_tagged_ptrs = 0;
  _relocated_ptr_info._num_nulled_ptrs = 0;
  assert(_current == nullptr, "must be");
  _current = this;
}

ArchiveBuilder::~ArchiveBuilder() {
  assert(_current == this, "must be");
  _current = nullptr;

  for (int i = 0; i < _symbols->length(); i++) {
    _symbols->at(i)->decrement_refcount();
  }

  delete _klasses;
  delete _symbols;
  if (_shared_rs.is_reserved()) {
    MemoryReserver::release(_shared_rs);
  }

  AOTArtifactFinder::dispose();
}

// Returns a deterministic sequence of pseudo random numbers. The main purpose is NOT
// for randomness but to get good entropy for the identity_hash() of archived Symbols,
// while keeping the contents of static CDS archives deterministic to ensure
// reproducibility of JDK builds.
int ArchiveBuilder::entropy() {
  assert(SafepointSynchronize::is_at_safepoint(), "needed to ensure deterministic sequence");
  _entropy_seed = os::next_random(_entropy_seed);
  return static_cast<int>(_entropy_seed);
}

class GatherKlassesAndSymbols : public UniqueMetaspaceClosure {
  ArchiveBuilder* _builder;

public:
  GatherKlassesAndSymbols(ArchiveBuilder* builder) : _builder(builder) {}

  virtual bool do_unique_ref(Ref* ref, bool read_only) {
    return _builder->gather_klass_and_symbol(ref, read_only);
  }
};

bool ArchiveBuilder::gather_klass_and_symbol(MetaspaceClosure::Ref* ref, bool read_only) {
  if (ref->obj() == nullptr) {
    return false;
  }
  if (get_follow_mode(ref) != make_a_copy) {
    return false;
  }
  if (ref->type() == MetaspaceClosureType::ClassType) {
    Klass* klass = (Klass*)ref->obj();
    assert(klass->is_klass(), "must be");
    if (!is_excluded(klass)) {
      _klasses->append(klass);
      if (klass->is_hidden()) {
        assert(klass->is_instance_klass(), "must be");
      }
    }
  } else if (ref->type() == MetaspaceClosureType::SymbolType) {
    // Make sure the symbol won't be GC'ed while we are dumping the archive.
    Symbol* sym = (Symbol*)ref->obj();
    sym->increment_refcount();
    _symbols->append(sym);
  }

  return true; // recurse
}

void ArchiveBuilder::gather_klasses_and_symbols() {
  ResourceMark rm;

  AOTArtifactFinder::initialize();
  AOTArtifactFinder::find_artifacts();

  aot_log_info(aot)("Gathering classes and symbols ... ");
  GatherKlassesAndSymbols doit(this);
  iterate_roots(&doit);
  doit.finish();

  if (CDSConfig::is_dumping_static_archive()) {
    // To ensure deterministic contents in the static archive, we need to ensure that
    // we iterate the MetaspaceObjs in a deterministic order. It doesn't matter where
    // the MetaspaceObjs are located originally, as they are copied sequentially into
    // the archive during the iteration.
    //
    // The only issue here is that the symbol table and the system directories may be
    // randomly ordered, so we copy the symbols and klasses into two arrays and sort
    // them deterministically.
    //
    // During -Xshare:dump, the order of Symbol creation is strictly determined by
    // the SharedClassListFile (class loading is done in a single thread and the JIT
    // is disabled). Also, Symbols are allocated in monotonically increasing addresses
    // (see Symbol::operator new(size_t, int)). So if we iterate the Symbols by
    // ascending address order, we ensure that all Symbols are copied into deterministic
    // locations in the archive.
    //
    // TODO: in the future, if we want to produce deterministic contents in the
    // dynamic archive, we might need to sort the symbols alphabetically (also see
    // DynamicArchiveBuilder::sort_methods()).
    aot_log_info(aot)("Sorting symbols ... ");
    _symbols->sort(compare_symbols_by_address);
    sort_klasses();
  }

  AOTClassLinker::add_candidates();
}

int ArchiveBuilder::compare_symbols_by_address(Symbol** a, Symbol** b) {
  if (a[0] < b[0]) {
    return -1;
  } else {
    assert(a[0] > b[0], "Duplicated symbol %s unexpected", (*a)->as_C_string());
    return 1;
  }
}

int ArchiveBuilder::compare_klass_by_name(Klass** a, Klass** b) {
  return a[0]->name()->fast_compare(b[0]->name());
}

void ArchiveBuilder::sort_klasses() {
  aot_log_info(aot)("Sorting classes ... ");
  _klasses->sort(compare_klass_by_name);
}

address ArchiveBuilder::reserve_buffer() {
  // AOTCodeCache::max_aot_code_size() accounts for aot code region.
  size_t buffer_size = LP64_ONLY(CompressedClassSpaceSize) NOT_LP64(256 * M) + AOTCodeCache::max_aot_code_size();
  ReservedSpace rs = MemoryReserver::reserve(buffer_size,
                                             AOTMetaspace::core_region_alignment(),
                                             os::vm_page_size(),
                                             mtNone);
  if (!rs.is_reserved()) {
    aot_log_error(aot)("Failed to reserve %zu bytes of output buffer.", buffer_size);
    AOTMetaspace::unrecoverable_writing_error();
  }

  // buffer_bottom is the lowest address of the 2 core regions (rw, ro) when
  // we are copying the class metadata into the buffer.
  address buffer_bottom = (address)rs.base();
  aot_log_info(aot)("Reserved output buffer space at " PTR_FORMAT " [%zu bytes]",
                p2i(buffer_bottom), buffer_size);
  _shared_rs = rs;

  _buffer_bottom = buffer_bottom;

  if (CDSConfig::is_dumping_static_archive()) {
    _current_dump_region = &_pz_region;
  } else {
    _current_dump_region = &_rw_region;
  }
  _current_dump_region->init(&_shared_rs, &_shared_vs);

  ArchivePtrMarker::initialize(&_ptrmap, &_shared_vs);

  // The bottom of the static archive should be mapped at this address by default.
  _requested_static_archive_bottom = (address)AOTMetaspace::requested_base_address();

  // The bottom of the archive (that I am writing now) should be mapped at this address by default.
  address my_archive_requested_bottom;

  if (CDSConfig::is_dumping_static_archive()) {
    my_archive_requested_bottom = _requested_static_archive_bottom;
  } else {
    _mapped_static_archive_bottom = (address)MetaspaceObj::aot_metaspace_base();
    _mapped_static_archive_top  = (address)MetaspaceObj::aot_metaspace_top();
    assert(_mapped_static_archive_top >= _mapped_static_archive_bottom, "must be");
    size_t static_archive_size = _mapped_static_archive_top - _mapped_static_archive_bottom;

    // At run time, we will mmap the dynamic archive at my_archive_requested_bottom
    _requested_static_archive_top = _requested_static_archive_bottom + static_archive_size;
    my_archive_requested_bottom = align_up(_requested_static_archive_top, AOTMetaspace::core_region_alignment());

    _requested_dynamic_archive_bottom = my_archive_requested_bottom;
  }

  _buffer_to_requested_delta = my_archive_requested_bottom - _buffer_bottom;

  address my_archive_requested_top = my_archive_requested_bottom + buffer_size;
  if (my_archive_requested_bottom <  _requested_static_archive_bottom ||
      my_archive_requested_top    <= _requested_static_archive_bottom) {
    // Size overflow.
    aot_log_error(aot)("my_archive_requested_bottom = " INTPTR_FORMAT, p2i(my_archive_requested_bottom));
    aot_log_error(aot)("my_archive_requested_top    = " INTPTR_FORMAT, p2i(my_archive_requested_top));
    aot_log_error(aot)("SharedBaseAddress (" INTPTR_FORMAT ") is too high. "
                   "Please rerun java -Xshare:dump with a lower value", p2i(_requested_static_archive_bottom));
    AOTMetaspace::unrecoverable_writing_error();
  }

  if (CDSConfig::is_dumping_static_archive()) {
    // We don't want any valid object to be at the very bottom of the archive.
    // See ArchivePtrMarker::mark_pointer().
    _pz_region.allocate(AOTMetaspace::protection_zone_size());
    start_dump_region(&_rw_region);
  }

  return buffer_bottom;
}

void ArchiveBuilder::iterate_sorted_roots(MetaspaceClosure* it) {
  int num_symbols = _symbols->length();
  for (int i = 0; i < num_symbols; i++) {
    it->push(_symbols->adr_at(i));
  }

  int num_klasses = _klasses->length();
  for (int i = 0; i < num_klasses; i++) {
    it->push(_klasses->adr_at(i));
  }

  iterate_roots(it);
}

class GatherSortedSourceObjs : public MetaspaceClosure {
  ArchiveBuilder* _builder;

public:
  GatherSortedSourceObjs(ArchiveBuilder* builder) : _builder(builder) {}

  virtual bool do_ref(Ref* ref, bool read_only) {
    return _builder->gather_one_source_obj(ref, read_only);
  }
};

bool ArchiveBuilder::gather_one_source_obj(MetaspaceClosure::Ref* ref, bool read_only) {
  address src_obj = ref->obj();
  if (src_obj == nullptr) {
    return false;
  }

  remember_embedded_pointer_in_enclosing_obj(ref);
  if (RegeneratedClasses::has_been_regenerated(src_obj)) {
    // No need to copy it. We will later relocate it to point to the regenerated klass/method.
    return false;
  }

  FollowMode follow_mode = get_follow_mode(ref);
  SourceObjInfo src_info(ref, read_only, follow_mode);
  bool created;
  SourceObjInfo* p = _src_obj_table.put_if_absent(src_obj, src_info, &created);
  if (created) {
    if (_src_obj_table.maybe_grow()) {
      log_info(aot, hashtables)("Expanded _src_obj_table table to %d", _src_obj_table.table_size());
    }
  }

#ifdef ASSERT
  if (ref->type() == MetaspaceClosureType::MethodType) {
    Method* m = (Method*)ref->obj();
    assert(!RegeneratedClasses::has_been_regenerated((address)m->method_holder()),
           "Should not archive methods in a class that has been regenerated");
  }
#endif

  if (ref->type() == MetaspaceClosureType::MethodDataType) {
    MethodData* md = (MethodData*)ref->obj();
    md->clean_method_data(false /* always_clean */);
  }

  assert(p->read_only() == src_info.read_only(), "must be");

  if (created && src_info.should_copy()) {
    if (read_only) {
      _ro_src_objs.append(p);
    } else {
      _rw_src_objs.append(p);
    }
    return true; // Need to recurse into this ref only if we are copying it
  } else {
    return false;
  }
}

void ArchiveBuilder::record_regenerated_object(address orig_src_obj, address regen_src_obj) {
  // Record the fact that orig_src_obj has been replaced by regen_src_obj. All calls to get_buffered_addr(orig_src_obj)
  // should return the same value as get_buffered_addr(regen_src_obj).
  SourceObjInfo* p = _src_obj_table.get(regen_src_obj);
  assert(p != nullptr, "regenerated object should always be dumped");
  SourceObjInfo orig_src_info(orig_src_obj, p);
  bool created;
  _src_obj_table.put_if_absent(orig_src_obj, orig_src_info, &created);
  assert(created, "We shouldn't have archived the original copy of a regenerated object");
}

// Remember that we have a pointer inside ref->enclosing_obj() that points to ref->obj()
void ArchiveBuilder::remember_embedded_pointer_in_enclosing_obj(MetaspaceClosure::Ref* ref) {
  assert(ref->obj() != nullptr, "should have checked");

  address enclosing_obj = ref->enclosing_obj();
  if (enclosing_obj == nullptr) {
    return;
  }

  // We are dealing with 3 addresses:
  // address o    = ref->obj(): We have found an object whose address is o.
  // address* mpp = ref->mpp(): The object o is pointed to by a pointer whose address is mpp.
  //                            I.e., (*mpp == o)
  // enclosing_obj            : If non-null, it is the object which has a field that points to o.
  //                            mpp is the address if that field.
  //
  // Example: We have an array whose first element points to a Method:
  //     Method* o                     = 0x0000abcd;
  //     Array<Method*>* enclosing_obj = 0x00001000;
  //     enclosing_obj->at_put(0, o);
  //
  // We the MetaspaceClosure iterates on the very first element of this array, we have
  //     ref->obj()           == 0x0000abcd   (the Method)
  //     ref->mpp()           == 0x00001008   (the location of the first element in the array)
  //     ref->enclosing_obj() == 0x00001000   (the Array that contains the Method)
  //
  // We use the above information to mark the bitmap to indicate that there's a pointer on address 0x00001008.
  SourceObjInfo* src_info = _src_obj_table.get(enclosing_obj);
  if (src_info == nullptr || !src_info->should_copy()) {
    // source objects of point_to_it/set_to_null types are not copied
    // so we don't need to remember their pointers.
  } else {
    if (src_info->read_only()) {
      _ro_src_objs.remember_embedded_pointer(src_info, ref);
    } else {
      _rw_src_objs.remember_embedded_pointer(src_info, ref);
    }
  }
}

void ArchiveBuilder::gather_source_objs() {
  ResourceMark rm;
  aot_log_info(aot)("Gathering all archivable objects ... ");
  gather_klasses_and_symbols();
  GatherSortedSourceObjs doit(this);
  iterate_sorted_roots(&doit);
  doit.finish();
}

bool ArchiveBuilder::is_excluded(Klass* klass) {
  if (klass->is_instance_klass()) {
    InstanceKlass* ik = InstanceKlass::cast(klass);
    return SystemDictionaryShared::is_excluded_class(ik);
  } else if (klass->is_objArray_klass()) {
    Klass* bottom = ObjArrayKlass::cast(klass)->bottom_klass();
    if (CDSConfig::is_dumping_dynamic_archive() && AOTMetaspace::in_aot_cache_static_region(bottom)) {
      // The bottom class is in the static archive so it's clearly not excluded.
      return false;
    } else if (bottom->is_instance_klass()) {
      return SystemDictionaryShared::is_excluded_class(InstanceKlass::cast(bottom));
    }
  }

  return false;
}

ArchiveBuilder::FollowMode ArchiveBuilder::get_follow_mode(MetaspaceClosure::Ref *ref) {
  address obj = ref->obj();
  if (CDSConfig::is_dumping_dynamic_archive() && AOTMetaspace::in_aot_cache(obj)) {
    // Don't dump existing shared metadata again.
    return point_to_it;
  } else if (ref->type() == MetaspaceClosureType::MethodDataType ||
             ref->type() == MetaspaceClosureType::MethodCountersType ||
             ref->type() == MetaspaceClosureType::KlassTrainingDataType ||
             ref->type() == MetaspaceClosureType::MethodTrainingDataType ||
             ref->type() == MetaspaceClosureType::CompileTrainingDataType) {
    return (TrainingData::need_data() || TrainingData::assembling_data()) ? make_a_copy : set_to_null;
  } else if (ref->type() == MetaspaceClosureType::AdapterHandlerEntryType) {
    return CDSConfig::is_dumping_adapters() ? make_a_copy : set_to_null;
  } else {
    if (ref->type() == MetaspaceClosureType::ClassType) {
      Klass* klass = (Klass*)ref->obj();
      assert(klass->is_klass(), "must be");
      if (RegeneratedClasses::has_been_regenerated(klass)) {
        klass = RegeneratedClasses::get_regenerated_object(klass);
      }
      if (is_excluded(klass)) {
        ResourceMark rm;
        aot_log_trace(aot)("pointer set to null: class (excluded): %s", klass->external_name());
        return set_to_null;
      }
      if (klass->is_array_klass() && CDSConfig::is_dumping_dynamic_archive()) {
        ResourceMark rm;
        aot_log_trace(aot)("pointer set to null: array class not supported in dynamic region: %s", klass->external_name());
        return set_to_null;
      }
    }

    return make_a_copy;
  }
}

void ArchiveBuilder::start_dump_region(DumpRegion* next) {
  current_dump_region()->pack(next);
  _current_dump_region = next;
}

char* ArchiveBuilder::ro_strdup(const char* s) {
  char* archived_str = ro_region_alloc((int)strlen(s) + 1);
  strcpy(archived_str, s);
  return archived_str;
}

// The objects that have embedded pointers will sink
// towards the end of the list. This ensures we have a maximum
// number of leading zero bits in the relocation bitmap.
int ArchiveBuilder::compare_src_objs(SourceObjInfo** a, SourceObjInfo** b) {
  if ((*a)->has_embedded_pointer() && !(*b)->has_embedded_pointer()) {
    return 1;
  } else if (!(*a)->has_embedded_pointer() && (*b)->has_embedded_pointer()) {
    return -1;
  } else {
    // This is necessary to keep the sorting order stable. Otherwise the
    // archive's contents may not be deterministic.
    return (*a)->id() - (*b)->id();
  }
}

void ArchiveBuilder::sort_metadata_objs() {
  _rw_src_objs.objs()->sort(compare_src_objs);
  _ro_src_objs.objs()->sort(compare_src_objs);
}

void ArchiveBuilder::dump_rw_metadata() {
  ResourceMark rm;
  aot_log_info(aot)("Allocating RW objects ... ");
  make_shallow_copies(&_rw_region, &_rw_src_objs);
}

void ArchiveBuilder::dump_ro_metadata() {
  ResourceMark rm;
  aot_log_info(aot)("Allocating RO objects ... ");

  start_dump_region(&_ro_region);
  make_shallow_copies(&_ro_region, &_ro_src_objs);
  RegeneratedClasses::record_regenerated_objects();
}

void ArchiveBuilder::make_shallow_copies(DumpRegion *dump_region,
                                         const ArchiveBuilder::SourceObjList* src_objs) {
  for (int i = 0; i < src_objs->objs()->length(); i++) {
    make_shallow_copy(dump_region, src_objs->objs()->at(i));
  }
  aot_log_info(aot)("done (%d objects)", src_objs->objs()->length());
}

void ArchiveBuilder::make_shallow_copy(DumpRegion *dump_region, SourceObjInfo* src_info) {
  address src = src_info->source_addr();
  int bytes = src_info->size_in_bytes(); // word-aligned
  size_t alignment = SharedSpaceObjectAlignment; // alignment for the dest pointer

  char* oldtop = dump_region->top();
  if (src_info->type() == MetaspaceClosureType::ClassType) {
    // Allocate space for a pointer directly in front of the future InstanceKlass, so
    // we can do a quick lookup from InstanceKlass* -> RunTimeClassInfo*
    // without building another hashtable. See RunTimeClassInfo::get_for()
    // in systemDictionaryShared.cpp.
    Klass* klass = (Klass*)src;
    if (klass->is_instance_klass()) {
      SystemDictionaryShared::validate_before_archiving(InstanceKlass::cast(klass));
      dump_region->allocate(sizeof(address));
    }
#ifdef _LP64
    // More strict alignments needed for UseCompressedClassPointers
    if (UseCompressedClassPointers) {
      alignment = nth_bit(ArchiveBuilder::precomputed_narrow_klass_shift());
    }
#endif
  } else if (src_info->type() == MetaspaceClosureType::SymbolType) {
    // Symbols may be allocated by using AllocateHeap, so their sizes
    // may be less than size_in_bytes() indicates.
    bytes = ((Symbol*)src)->byte_size();
  }

  char* dest = dump_region->allocate(bytes, alignment);
  memcpy(dest, src, bytes);

  // Update the hash of buffered sorted symbols for static dump so that the symbols have deterministic contents
  if (CDSConfig::is_dumping_static_archive() && (src_info->type() == MetaspaceClosureType::SymbolType)) {
    Symbol* buffered_symbol = (Symbol*)dest;
    assert(((Symbol*)src)->is_permanent(), "archived symbols must be permanent");
    buffered_symbol->update_identity_hash();
  }

  {
    bool created;
    _buffered_to_src_table.put_if_absent((address)dest, src, &created);
    assert(created, "must be");
    if (_buffered_to_src_table.maybe_grow()) {
      log_info(aot, hashtables)("Expanded _buffered_to_src_table table to %d", _buffered_to_src_table.table_size());
    }
  }

  intptr_t* archived_vtable = CppVtables::get_archived_vtable(src_info->type(), (address)dest);
  if (archived_vtable != nullptr) {
    *(address*)dest = (address)archived_vtable;
    ArchivePtrMarker::mark_pointer((address*)dest);
  }

  log_trace(aot)("Copy: " PTR_FORMAT " ==> " PTR_FORMAT " %d", p2i(src), p2i(dest), bytes);
  src_info->set_buffered_addr((address)dest);

  char* newtop = dump_region->top();
  _alloc_stats.record(src_info->type(), int(newtop - oldtop), src_info->read_only());

  DEBUG_ONLY(_alloc_stats.verify((int)dump_region->used(), src_info->read_only()));
}

// This is used by code that hand-assembles data structures, such as the LambdaProxyClassKey, that are
// not handled by MetaspaceClosure.
void ArchiveBuilder::write_pointer_in_buffer(address* ptr_location, address src_addr) {
  assert(is_in_buffer_space(ptr_location), "must be");
  if (src_addr == nullptr) {
    *ptr_location = nullptr;
    ArchivePtrMarker::clear_pointer(ptr_location);
  } else {
    *ptr_location = get_buffered_addr(src_addr);
    ArchivePtrMarker::mark_pointer(ptr_location);
  }
}

void ArchiveBuilder::mark_and_relocate_to_buffered_addr(address* ptr_location) {
  assert(*ptr_location != nullptr, "sanity");
  if (!is_in_mapped_static_archive(*ptr_location)) {
    *ptr_location = get_buffered_addr(*ptr_location);
  }
  ArchivePtrMarker::mark_pointer(ptr_location);
}

bool ArchiveBuilder::has_been_archived(address src_addr) const {
  SourceObjInfo* p = _src_obj_table.get(src_addr);
  if (p == nullptr) {
    // This object has never been seen by ArchiveBuilder
    return false;
  }
  if (p->buffered_addr() == nullptr) {
    // ArchiveBuilder has seen this object, but decided not to archive it. So
    // Any reference to this object will be modified to nullptr inside the buffer.
    assert(p->follow_mode() == set_to_null, "must be");
    return false;
  }

  DEBUG_ONLY({
    // This is a class/method that belongs to one of the "original" classes that
    // have been regenerated by lambdaFormInvokers.cpp. We must have archived
    // the "regenerated" version of it.
    if (RegeneratedClasses::has_been_regenerated(src_addr)) {
      address regen_obj = RegeneratedClasses::get_regenerated_object(src_addr);
      precond(regen_obj != nullptr && regen_obj != src_addr);
      assert(has_been_archived(regen_obj), "must be");
      assert(get_buffered_addr(src_addr) == get_buffered_addr(regen_obj), "must be");
    }});

  return true;
}

address ArchiveBuilder::get_buffered_addr(address src_addr) const {
  SourceObjInfo* p = _src_obj_table.get(src_addr);
  assert(p != nullptr, "src_addr " INTPTR_FORMAT " is used but has not been archived",
         p2i(src_addr));

  return p->buffered_addr();
}

address ArchiveBuilder::get_source_addr(address buffered_addr) const {
  assert(is_in_buffer_space(buffered_addr), "must be");
  address* src_p = _buffered_to_src_table.get(buffered_addr);
  assert(src_p != nullptr && *src_p != nullptr, "must be");
  return *src_p;
}

void ArchiveBuilder::relocate_embedded_pointers(ArchiveBuilder::SourceObjList* src_objs) {
  for (int i = 0; i < src_objs->objs()->length(); i++) {
    src_objs->relocate(i, this);
  }
}

void ArchiveBuilder::relocate_metaspaceobj_embedded_pointers() {
  aot_log_info(aot)("Relocating embedded pointers in core regions ... ");
  relocate_embedded_pointers(&_rw_src_objs);
  relocate_embedded_pointers(&_ro_src_objs);
  log_info(cds)("Relocating %zu pointers, %zu tagged, %zu nulled",
                _relocated_ptr_info._num_ptrs,
                _relocated_ptr_info._num_tagged_ptrs,
                _relocated_ptr_info._num_nulled_ptrs);
}

#define ADD_COUNT(x) \
  x += 1; \
  x ## _a += aotlinked ? 1 : 0; \
  x ## _i += inited ? 1 : 0;

#define DECLARE_INSTANCE_KLASS_COUNTER(x) \
  int x = 0; \
  int x ## _a = 0; \
  int x ## _i = 0;

void ArchiveBuilder::make_klasses_shareable() {
  DECLARE_INSTANCE_KLASS_COUNTER(num_instance_klasses);
  DECLARE_INSTANCE_KLASS_COUNTER(num_boot_klasses);
  DECLARE_INSTANCE_KLASS_COUNTER(num_vm_klasses);
  DECLARE_INSTANCE_KLASS_COUNTER(num_platform_klasses);
  DECLARE_INSTANCE_KLASS_COUNTER(num_app_klasses);
  DECLARE_INSTANCE_KLASS_COUNTER(num_old_klasses);
  DECLARE_INSTANCE_KLASS_COUNTER(num_hidden_klasses);
  DECLARE_INSTANCE_KLASS_COUNTER(num_enum_klasses);
  DECLARE_INSTANCE_KLASS_COUNTER(num_unregistered_klasses);
  int num_unlinked_klasses = 0;
  int num_obj_array_klasses = 0;
  int num_type_array_klasses = 0;

  int boot_unlinked = 0;
  int platform_unlinked = 0;
  int app_unlinked = 0;
  int unreg_unlinked = 0;

  for (int i = 0; i < klasses()->length(); i++) {
    // Some of the code in ConstantPool::remove_unshareable_info() requires the classes
    // to be in linked state, so it must be call here before the next loop, which returns
    // all classes to unlinked state.
    Klass* k = get_buffered_addr(klasses()->at(i));
    if (k->is_instance_klass()) {
      InstanceKlass::cast(k)->constants()->remove_unshareable_info();
    }
  }

  for (int i = 0; i < klasses()->length(); i++) {
    const char* type;
    const char* unlinked = "";
    const char* kind = "";
    const char* hidden = "";
    const char* old = "";
    const char* generated = "";
    const char* aotlinked_msg = "";
    const char* inited_msg = "";
    Klass* k = get_buffered_addr(klasses()->at(i));
    bool inited = false;
    k->remove_java_mirror();
#ifdef _LP64
    if (UseCompactObjectHeaders) {
      Klass* requested_k = to_requested(k);
      address narrow_klass_base = _requested_static_archive_bottom; // runtime encoding base == runtime mapping start
      const int narrow_klass_shift = precomputed_narrow_klass_shift();
      narrowKlass nk = CompressedKlassPointers::encode_not_null_without_asserts(requested_k, narrow_klass_base, narrow_klass_shift);
      k->set_prototype_header(markWord::prototype().set_narrow_klass(nk));
    }
#endif //_LP64
    if (k->is_objArray_klass()) {
      // InstanceKlass and TypeArrayKlass will in turn call remove_unshareable_info
      // on their array classes.
      num_obj_array_klasses ++;
      type = "array";
    } else if (k->is_typeArray_klass()) {
      num_type_array_klasses ++;
      type = "array";
      k->remove_unshareable_info();
    } else {
      assert(k->is_instance_klass(), " must be");
      InstanceKlass* ik = InstanceKlass::cast(k);
      InstanceKlass* src_ik = get_source_addr(ik);
      bool aotlinked = AOTClassLinker::is_candidate(src_ik);
      inited = ik->has_aot_initialized_mirror();
      ADD_COUNT(num_instance_klasses);
      if (ik->is_hidden()) {
        ADD_COUNT(num_hidden_klasses);
        hidden = " hidden";
        oop loader = k->class_loader();
        if (loader == nullptr) {
          type = "boot";
          ADD_COUNT(num_boot_klasses);
        } else if (loader == SystemDictionary::java_platform_loader()) {
          type = "plat";
          ADD_COUNT(num_platform_klasses);
        } else if (loader == SystemDictionary::java_system_loader()) {
          type = "app";
          ADD_COUNT(num_app_klasses);
        } else {
          type = "bad";
          assert(0, "shouldn't happen");
        }
        if (CDSConfig::is_dumping_method_handles()) {
          assert(HeapShared::is_archivable_hidden_klass(ik), "sanity");
        } else {
          // Legacy CDS support for lambda proxies
          CDS_JAVA_HEAP_ONLY(assert(HeapShared::is_lambda_proxy_klass(ik), "sanity");)
        }
      } else if (ik->defined_by_boot_loader()) {
        type = "boot";
        ADD_COUNT(num_boot_klasses);
      } else if (ik->defined_by_platform_loader()) {
        type = "plat";
        ADD_COUNT(num_platform_klasses);
      } else if (ik->defined_by_app_loader()) {
        type = "app";
        ADD_COUNT(num_app_klasses);
      } else {
        assert(ik->defined_by_other_loaders(), "must be");
        type = "unreg";
        ADD_COUNT(num_unregistered_klasses);
      }

      if (AOTClassLinker::is_vm_class(src_ik)) {
        ADD_COUNT(num_vm_klasses);
      }

      if (!ik->is_linked()) {
        num_unlinked_klasses ++;
        unlinked = " unlinked";
        if (ik->defined_by_boot_loader()) {
          boot_unlinked ++;
        } else if (ik->defined_by_platform_loader()) {
          platform_unlinked ++;
        } else if (ik->defined_by_app_loader()) {
          app_unlinked ++;
        } else {
          unreg_unlinked ++;
        }
      }

      if (ik->is_interface()) {
        kind = " interface";
      } else if (src_ik->is_enum_subclass()) {
        kind = " enum";
        ADD_COUNT(num_enum_klasses);
      }

      if (CDSConfig::is_old_class_for_verifier(ik)) {
        ADD_COUNT(num_old_klasses);
        old = " old";
      }

      if (ik->is_aot_generated_class()) {
        generated = " generated";
      }
      if (aotlinked) {
        aotlinked_msg = " aot-linked";
      }
      if (inited) {
        if (InstanceKlass::cast(k)->static_field_size() == 0) {
          inited_msg = " inited (no static fields)";
        } else {
          inited_msg = " inited";
        }
      }

      AOTMetaspace::rewrite_bytecodes_and_calculate_fingerprints(Thread::current(), ik);
      ik->remove_unshareable_info();
    }

    if (aot_log_is_enabled(Debug, aot, class)) {
      ResourceMark rm;
      aot_log_debug(aot, class)("klasses[%5d] = " PTR_FORMAT " %-5s %s%s%s%s%s%s%s%s", i,
                            p2i(to_requested(k)), type, k->external_name(),
                            kind, hidden, old, unlinked, generated, aotlinked_msg, inited_msg);
    }
  }

#define STATS_FORMAT    "= %5d, aot-linked = %5d, inited = %5d"
#define STATS_PARAMS(x) num_ ## x, num_ ## x ## _a, num_ ## x ## _i

  aot_log_info(aot)("Number of classes %d", num_instance_klasses + num_obj_array_klasses + num_type_array_klasses);
  aot_log_info(aot)("    instance classes   " STATS_FORMAT, STATS_PARAMS(instance_klasses));
  aot_log_info(aot)("      boot             " STATS_FORMAT, STATS_PARAMS(boot_klasses));
  aot_log_info(aot)("        vm             " STATS_FORMAT, STATS_PARAMS(vm_klasses));
  aot_log_info(aot)("      platform         " STATS_FORMAT, STATS_PARAMS(platform_klasses));
  aot_log_info(aot)("      app              " STATS_FORMAT, STATS_PARAMS(app_klasses));
  aot_log_info(aot)("      unregistered     " STATS_FORMAT, STATS_PARAMS(unregistered_klasses));
  aot_log_info(aot)("      (enum)           " STATS_FORMAT, STATS_PARAMS(enum_klasses));
  aot_log_info(aot)("      (hidden)         " STATS_FORMAT, STATS_PARAMS(hidden_klasses));
  aot_log_info(aot)("      (old)            " STATS_FORMAT, STATS_PARAMS(old_klasses));
  aot_log_info(aot)("      (unlinked)       = %5d, boot = %d, plat = %d, app = %d, unreg = %d",
                num_unlinked_klasses, boot_unlinked, platform_unlinked, app_unlinked, unreg_unlinked);
  aot_log_info(aot)("    obj array classes  = %5d", num_obj_array_klasses);
  aot_log_info(aot)("    type array classes = %5d", num_type_array_klasses);
  aot_log_info(aot)("               symbols = %5d", _symbols->length());

#undef STATS_FORMAT
#undef STATS_PARAMS
}

void ArchiveBuilder::make_training_data_shareable() {
  auto clean_td = [&] (address& src_obj,  SourceObjInfo& info) {
    if (!is_in_buffer_space(info.buffered_addr())) {
      return;
    }

    if (info.type() == MetaspaceClosureType::KlassTrainingDataType ||
        info.type() == MetaspaceClosureType::MethodTrainingDataType ||
        info.type() == MetaspaceClosureType::CompileTrainingDataType) {
      TrainingData* buffered_td = (TrainingData*)info.buffered_addr();
      buffered_td->remove_unshareable_info();
    } else if (info.type() == MetaspaceClosureType::MethodDataType) {
      MethodData* buffered_mdo = (MethodData*)info.buffered_addr();
      buffered_mdo->remove_unshareable_info();
    } else if (info.type() == MetaspaceClosureType::MethodCountersType) {
      MethodCounters* buffered_mc = (MethodCounters*)info.buffered_addr();
      buffered_mc->remove_unshareable_info();
    }
  };
  _src_obj_table.iterate_all(clean_td);
}

uintx ArchiveBuilder::buffer_to_offset(address p) const {
  address requested_p = to_requested(p);
  assert(requested_p >= _requested_static_archive_bottom, "must be");
  return requested_p - _requested_static_archive_bottom;
}

uintx ArchiveBuilder::any_to_offset(address p) const {
  if (is_in_mapped_static_archive(p)) {
    assert(CDSConfig::is_dumping_dynamic_archive(), "must be");
    return p - _mapped_static_archive_bottom;
  }
  if (!is_in_buffer_space(p)) {
    // p must be a "source" address
    p = get_buffered_addr(p);
  }
  return buffer_to_offset(p);
}

address ArchiveBuilder::offset_to_buffered_address(u4 offset) const {
  address requested_addr = _requested_static_archive_bottom + offset;
  address buffered_addr = requested_addr - _buffer_to_requested_delta;
  assert(is_in_buffer_space(buffered_addr), "bad offset");
  return buffered_addr;
}

void ArchiveBuilder::start_ac_region() {
  ro_region()->pack();
  start_dump_region(&_ac_region);
}

void ArchiveBuilder::end_ac_region() {
  _ac_region.pack();
}

#if INCLUDE_CDS_JAVA_HEAP
narrowKlass ArchiveBuilder::get_requested_narrow_klass(Klass* k) {
  assert(CDSConfig::is_dumping_heap(), "sanity");
  k = get_buffered_klass(k);
  Klass* requested_k = to_requested(k);
  const int narrow_klass_shift = ArchiveBuilder::precomputed_narrow_klass_shift();
#ifdef ASSERT
  const size_t klass_alignment = MAX2(SharedSpaceObjectAlignment, (size_t)nth_bit(narrow_klass_shift));
  assert(is_aligned(k, klass_alignment), "Klass " PTR_FORMAT " misaligned.", p2i(k));
#endif
  address narrow_klass_base = _requested_static_archive_bottom; // runtime encoding base == runtime mapping start
  // Note: use the "raw" version of encode that takes explicit narrow klass base and shift. Don't use any
  // of the variants that do sanity checks, nor any of those that use the current - dump - JVM's encoding setting.
  return CompressedKlassPointers::encode_not_null_without_asserts(requested_k, narrow_klass_base, narrow_klass_shift);
}
#endif // INCLUDE_CDS_JAVA_HEAP

// RelocateBufferToRequested --- Relocate all the pointers in rw/ro,
// so that the archive can be mapped to the "requested" location without runtime relocation.
//
// - See ArchiveBuilder header for the definition of "buffer", "mapped" and "requested"
// - ArchivePtrMarker::ptrmap() marks all the pointers in the rw/ro regions
// - Every pointer must have one of the following values:
//   [a] nullptr:
//       No relocation is needed. Remove this pointer from ptrmap so we don't need to
//       consider it at runtime.
//   [b] Points into an object X which is inside the buffer:
//       Adjust this pointer by _buffer_to_requested_delta, so it points to X
//       when the archive is mapped at the requested location.
//   [c] Points into an object Y which is inside mapped static archive:
//       - This happens only during dynamic dump
//       - Adjust this pointer by _mapped_to_requested_static_archive_delta,
//         so it points to Y when the static archive is mapped at the requested location.
template <bool STATIC_DUMP>
class RelocateBufferToRequested : public BitMapClosure {
  ArchiveBuilder* _builder;
  address _buffer_bottom;
  intx _buffer_to_requested_delta;
  intx _mapped_to_requested_static_archive_delta;
  size_t _max_non_null_offset;

 public:
  RelocateBufferToRequested(ArchiveBuilder* builder) {
    _builder = builder;
    _buffer_bottom = _builder->buffer_bottom();
    _buffer_to_requested_delta = builder->buffer_to_requested_delta();
    _mapped_to_requested_static_archive_delta = builder->requested_static_archive_bottom() - builder->mapped_static_archive_bottom();
    _max_non_null_offset = 0;

    address bottom = _builder->buffer_bottom();
    address top = _builder->buffer_top();
    address new_bottom = bottom + _buffer_to_requested_delta;
    address new_top = top + _buffer_to_requested_delta;
    aot_log_debug(aot)("Relocating archive from [" INTPTR_FORMAT " - " INTPTR_FORMAT "] to "
                   "[" INTPTR_FORMAT " - " INTPTR_FORMAT "]",
                   p2i(bottom), p2i(top),
                   p2i(new_bottom), p2i(new_top));
  }

  bool do_bit(size_t offset) {
    address* p = (address*)_buffer_bottom + offset;
    assert(_builder->is_in_buffer_space(p), "pointer must live in buffer space");

    if (*p == nullptr) {
      // todo -- clear bit, etc
      ArchivePtrMarker::ptrmap()->clear_bit(offset);
    } else {
      if (STATIC_DUMP) {
        assert(_builder->is_in_buffer_space(*p), "old pointer must point inside buffer space");
        *p += _buffer_to_requested_delta;
        assert(_builder->is_in_requested_static_archive(*p), "new pointer must point inside requested archive");
      } else {
        if (_builder->is_in_buffer_space(*p)) {
          *p += _buffer_to_requested_delta;
          // assert is in requested dynamic archive
        } else {
          assert(_builder->is_in_mapped_static_archive(*p), "old pointer must point inside buffer space or mapped static archive");
          *p += _mapped_to_requested_static_archive_delta;
          assert(_builder->is_in_requested_static_archive(*p), "new pointer must point inside requested archive");
        }
      }
      _max_non_null_offset = offset;
    }

    return true; // keep iterating
  }

  void doit() {
    ArchivePtrMarker::ptrmap()->iterate(this);
    ArchivePtrMarker::compact(_max_non_null_offset);
  }
};

#ifdef _LP64
int ArchiveBuilder::precomputed_narrow_klass_shift() {
  // Legacy Mode:
  //    We use 32 bits for narrowKlass, which should cover the full 4G Klass range. Shift can be 0.
  // CompactObjectHeader Mode:
  //    narrowKlass is much smaller, and we use the highest possible shift value to later get the maximum
  //    Klass encoding range.
  //
  // Note that all of this may change in the future, if we decide to correct the pre-calculated
  // narrow Klass IDs at archive load time.
  assert(UseCompressedClassPointers, "Only needed for compressed class pointers");
  return UseCompactObjectHeaders ?  CompressedKlassPointers::max_shift() : 0;
}
#endif // _LP64

void ArchiveBuilder::relocate_to_requested() {
  if (!ro_region()->is_packed()) {
    ro_region()->pack();
  }
  size_t my_archive_size = buffer_top() - buffer_bottom();

  if (CDSConfig::is_dumping_static_archive()) {
    _requested_static_archive_top = _requested_static_archive_bottom + my_archive_size;
    RelocateBufferToRequested<true> patcher(this);
    patcher.doit();
  } else {
    assert(CDSConfig::is_dumping_dynamic_archive(), "must be");
    _requested_dynamic_archive_top = _requested_dynamic_archive_bottom + my_archive_size;
    RelocateBufferToRequested<false> patcher(this);
    patcher.doit();
  }
}

void ArchiveBuilder::print_stats() {
  _alloc_stats.print_stats(int(_ro_region.used()), int(_rw_region.used()));
}

void ArchiveBuilder::write_archive(FileMapInfo* mapinfo, ArchiveMappedHeapInfo* mapped_heap_info, ArchiveStreamedHeapInfo* streamed_heap_info) {
  // Make sure NUM_CDS_REGIONS (exported in cds.h) agrees with
  // AOTMetaspace::n_regions (internal to hotspot).
  assert(NUM_CDS_REGIONS == AOTMetaspace::n_regions, "sanity");

  ResourceMark rm;

  write_region(mapinfo, AOTMetaspace::rw, &_rw_region, /*read_only=*/false,/*allow_exec=*/false);
  write_region(mapinfo, AOTMetaspace::ro, &_ro_region, /*read_only=*/true, /*allow_exec=*/false);
  write_region(mapinfo, AOTMetaspace::ac, &_ac_region, /*read_only=*/false,/*allow_exec=*/false);

  // Split pointer map into read-write and read-only bitmaps
  ArchivePtrMarker::initialize_rw_ro_maps(&_rw_ptrmap, &_ro_ptrmap);

  size_t bitmap_size_in_bytes;
  char* bitmap = mapinfo->write_bitmap_region(ArchivePtrMarker::rw_ptrmap(),
                                              ArchivePtrMarker::ro_ptrmap(),
                                              mapped_heap_info,
                                              streamed_heap_info,
                                              bitmap_size_in_bytes);

  if (mapped_heap_info != nullptr && mapped_heap_info->is_used()) {
    _total_heap_region_size = mapinfo->write_mapped_heap_region(mapped_heap_info);
  } else if (streamed_heap_info != nullptr && streamed_heap_info->is_used()) {
    _total_heap_region_size = mapinfo->write_streamed_heap_region(streamed_heap_info);
  }

  print_region_stats(mapinfo, mapped_heap_info, streamed_heap_info);

  mapinfo->set_requested_base((char*)AOTMetaspace::requested_base_address());
  mapinfo->set_header_crc(mapinfo->compute_header_crc());
  // After this point, we should not write any data into mapinfo->header() since this
  // would corrupt its checksum we have calculated before.
  mapinfo->write_header();
  mapinfo->close();

  if (log_is_enabled(Info, aot)) {
    log_info(aot)("Full module graph = %s", CDSConfig::is_dumping_full_module_graph() ? "enabled" : "disabled");
    print_stats();
  }

  if (log_is_enabled(Info, aot, map)) {
    AOTMapLogger::dumptime_log(this, mapinfo, mapped_heap_info, streamed_heap_info, bitmap, bitmap_size_in_bytes);
  }
  CDS_JAVA_HEAP_ONLY(HeapShared::destroy_archived_object_cache());
  FREE_C_HEAP_ARRAY(char, bitmap);
}

void ArchiveBuilder::write_region(FileMapInfo* mapinfo, int region_idx, DumpRegion* dump_region, bool read_only,  bool allow_exec) {
  mapinfo->write_region(region_idx, dump_region->base(), dump_region->used(), read_only, allow_exec);
}

void ArchiveBuilder::count_relocated_pointer(bool tagged, bool nulled) {
  _relocated_ptr_info._num_ptrs ++;
  _relocated_ptr_info._num_tagged_ptrs += tagged ? 1 : 0;
  _relocated_ptr_info._num_nulled_ptrs += nulled ? 1 : 0;
}

void ArchiveBuilder::print_region_stats(FileMapInfo *mapinfo,
                                        ArchiveMappedHeapInfo* mapped_heap_info,
                                        ArchiveStreamedHeapInfo* streamed_heap_info) {
  // Print statistics of all the regions
  const size_t bitmap_used = mapinfo->region_at(AOTMetaspace::bm)->used();
  const size_t bitmap_reserved = mapinfo->region_at(AOTMetaspace::bm)->used_aligned();
  const size_t total_reserved = _ro_region.reserved()  + _rw_region.reserved() +
                                bitmap_reserved +
                                _total_heap_region_size;
  const size_t total_bytes = _ro_region.used()  + _rw_region.used() +
                             bitmap_used +
                             _total_heap_region_size;
  const double total_u_perc = percent_of(total_bytes, total_reserved);

  _rw_region.print(total_reserved);
  _ro_region.print(total_reserved);
  _ac_region.print(total_reserved);

  print_bitmap_region_stats(bitmap_used, total_reserved);

  if (mapped_heap_info != nullptr && mapped_heap_info->is_used()) {
    print_heap_region_stats(mapped_heap_info->buffer_start(), mapped_heap_info->buffer_byte_size(), total_reserved);
  } else if (streamed_heap_info != nullptr && streamed_heap_info->is_used()) {
    print_heap_region_stats(streamed_heap_info->buffer_start(), streamed_heap_info->buffer_byte_size(), total_reserved);
  }

  aot_log_debug(aot)("total   : %9zu [100.0%% of total] out of %9zu bytes [%5.1f%% used]",
                     total_bytes, total_reserved, total_u_perc);
}

void ArchiveBuilder::print_bitmap_region_stats(size_t size, size_t total_size) {
  aot_log_debug(aot)("bm space: %9zu [ %4.1f%% of total] out of %9zu bytes [100.0%% used]",
                     size, size/double(total_size)*100.0, size);
}

void ArchiveBuilder::print_heap_region_stats(char* start, size_t size, size_t total_size) {
  char* top = start + size;
  aot_log_debug(aot)("hp space: %9zu [ %4.1f%% of total] out of %9zu bytes [100.0%% used] at " INTPTR_FORMAT,
                     size, size/double(total_size)*100.0, size, p2i(start));
}

void ArchiveBuilder::report_out_of_space(const char* name, size_t needed_bytes) {
  // This is highly unlikely to happen on 64-bits because we have reserved a 4GB space.
  // On 32-bit we reserve only 256MB so you could run out of space with 100,000 classes
  // or so.
  _rw_region.print_out_of_space_msg(name, needed_bytes);
  _ro_region.print_out_of_space_msg(name, needed_bytes);

  log_error(aot)("Unable to allocate from '%s' region: Please reduce the number of shared classes.", name);
  AOTMetaspace::unrecoverable_writing_error();
}
