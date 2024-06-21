/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/archiveBuilder.hpp"
#include "cds/archiveHeapWriter.hpp"
#include "cds/archiveUtils.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/cppVtables.hpp"
#include "cds/dumpAllocStats.hpp"
#include "cds/dynamicArchive.hpp"
#include "cds/heapShared.hpp"
#include "cds/metaspaceShared.hpp"
#include "cds/regeneratedClasses.hpp"
#include "classfile/classLoaderDataShared.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "interpreter/abstractInterpreter.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allStatic.hpp"
#include "memory/memRegion.hpp"
#include "memory/resourceArea.hpp"
#include "oops/compressedKlass.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
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

    address old_p = *ptr_loc;
    address new_p = _builder->get_buffered_addr(old_p);

    log_trace(cds)("Ref: [" PTR_FORMAT "] -> " PTR_FORMAT " => " PTR_FORMAT,
                   p2i(ptr_loc), p2i(old_p), p2i(new_p));

    ArchivePtrMarker::set_and_mark_pointer(ptr_loc, new_p);
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
  _last_verified_top(nullptr),
  _num_dump_regions_used(0),
  _other_region_used_bytes(0),
  _requested_static_archive_bottom(nullptr),
  _requested_static_archive_top(nullptr),
  _requested_dynamic_archive_bottom(nullptr),
  _requested_dynamic_archive_top(nullptr),
  _mapped_static_archive_bottom(nullptr),
  _mapped_static_archive_top(nullptr),
  _buffer_to_requested_delta(0),
  _rw_region("rw", MAX_SHARED_DELTA),
  _ro_region("ro", MAX_SHARED_DELTA),
  _ptrmap(mtClassShared),
  _rw_ptrmap(mtClassShared),
  _ro_ptrmap(mtClassShared),
  _rw_src_objs(),
  _ro_src_objs(),
  _src_obj_table(INITIAL_TABLE_SIZE, MAX_TABLE_SIZE),
  _buffered_to_src_table(INITIAL_TABLE_SIZE, MAX_TABLE_SIZE),
  _total_heap_region_size(0),
  _estimated_metaspaceobj_bytes(0),
  _estimated_hashtable_bytes(0)
{
  _klasses = new (mtClassShared) GrowableArray<Klass*>(4 * K, mtClassShared);
  _symbols = new (mtClassShared) GrowableArray<Symbol*>(256 * K, mtClassShared);
  _entropy_seed = 0x12345678;
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
    _shared_rs.release();
  }
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
  if (ref->msotype() == MetaspaceObj::ClassType) {
    Klass* klass = (Klass*)ref->obj();
    assert(klass->is_klass(), "must be");
    if (!is_excluded(klass)) {
      _klasses->append(klass);
    }
    // See RunTimeClassInfo::get_for()
    _estimated_metaspaceobj_bytes += align_up(BytesPerWord, SharedSpaceObjectAlignment);
  } else if (ref->msotype() == MetaspaceObj::SymbolType) {
    // Make sure the symbol won't be GC'ed while we are dumping the archive.
    Symbol* sym = (Symbol*)ref->obj();
    sym->increment_refcount();
    _symbols->append(sym);
  }

  int bytes = ref->size() * BytesPerWord;
  _estimated_metaspaceobj_bytes += align_up(bytes, SharedSpaceObjectAlignment);

  return true; // recurse
}

void ArchiveBuilder::gather_klasses_and_symbols() {
  ResourceMark rm;
  log_info(cds)("Gathering classes and symbols ... ");
  GatherKlassesAndSymbols doit(this);
  iterate_roots(&doit);
#if INCLUDE_CDS_JAVA_HEAP
  if (CDSConfig::is_dumping_full_module_graph()) {
    ClassLoaderDataShared::iterate_symbols(&doit);
  }
#endif
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
    log_info(cds)("Sorting symbols ... ");
    _symbols->sort(compare_symbols_by_address);
    sort_klasses();

    // TODO -- we need a proper estimate for the archived modules, etc,
    // but this should be enough for now
    _estimated_metaspaceobj_bytes += 200 * 1024 * 1024;
  }
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
  log_info(cds)("Sorting classes ... ");
  _klasses->sort(compare_klass_by_name);
}

size_t ArchiveBuilder::estimate_archive_size() {
  // size of the symbol table and two dictionaries, plus the RunTimeClassInfo's
  size_t symbol_table_est = SymbolTable::estimate_size_for_archive();
  size_t dictionary_est = SystemDictionaryShared::estimate_size_for_archive();
  _estimated_hashtable_bytes = symbol_table_est + dictionary_est;

  size_t total = 0;

  total += _estimated_metaspaceobj_bytes;
  total += _estimated_hashtable_bytes;

  // allow fragmentation at the end of each dump region
  total += _total_dump_regions * MetaspaceShared::core_region_alignment();

  log_info(cds)("_estimated_hashtable_bytes = " SIZE_FORMAT " + " SIZE_FORMAT " = " SIZE_FORMAT,
                symbol_table_est, dictionary_est, _estimated_hashtable_bytes);
  log_info(cds)("_estimated_metaspaceobj_bytes = " SIZE_FORMAT, _estimated_metaspaceobj_bytes);
  log_info(cds)("total estimate bytes = " SIZE_FORMAT, total);

  return align_up(total, MetaspaceShared::core_region_alignment());
}

address ArchiveBuilder::reserve_buffer() {
  size_t buffer_size = estimate_archive_size();
  ReservedSpace rs(buffer_size, MetaspaceShared::core_region_alignment(), os::vm_page_size());
  if (!rs.is_reserved()) {
    log_error(cds)("Failed to reserve " SIZE_FORMAT " bytes of output buffer.", buffer_size);
    MetaspaceShared::unrecoverable_writing_error();
  }

  // buffer_bottom is the lowest address of the 2 core regions (rw, ro) when
  // we are copying the class metadata into the buffer.
  address buffer_bottom = (address)rs.base();
  log_info(cds)("Reserved output buffer space at " PTR_FORMAT " [" SIZE_FORMAT " bytes]",
                p2i(buffer_bottom), buffer_size);
  _shared_rs = rs;

  _buffer_bottom = buffer_bottom;
  _last_verified_top = buffer_bottom;
  _current_dump_region = &_rw_region;
  _num_dump_regions_used = 1;
  _other_region_used_bytes = 0;
  _current_dump_region->init(&_shared_rs, &_shared_vs);

  ArchivePtrMarker::initialize(&_ptrmap, &_shared_vs);

  // The bottom of the static archive should be mapped at this address by default.
  _requested_static_archive_bottom = (address)MetaspaceShared::requested_base_address();

  // The bottom of the archive (that I am writing now) should be mapped at this address by default.
  address my_archive_requested_bottom;

  if (CDSConfig::is_dumping_static_archive()) {
    my_archive_requested_bottom = _requested_static_archive_bottom;
  } else {
    _mapped_static_archive_bottom = (address)MetaspaceObj::shared_metaspace_base();
    _mapped_static_archive_top  = (address)MetaspaceObj::shared_metaspace_top();
    assert(_mapped_static_archive_top >= _mapped_static_archive_bottom, "must be");
    size_t static_archive_size = _mapped_static_archive_top - _mapped_static_archive_bottom;

    // At run time, we will mmap the dynamic archive at my_archive_requested_bottom
    _requested_static_archive_top = _requested_static_archive_bottom + static_archive_size;
    my_archive_requested_bottom = align_up(_requested_static_archive_top, MetaspaceShared::core_region_alignment());

    _requested_dynamic_archive_bottom = my_archive_requested_bottom;
  }

  _buffer_to_requested_delta = my_archive_requested_bottom - _buffer_bottom;

  address my_archive_requested_top = my_archive_requested_bottom + buffer_size;
  if (my_archive_requested_bottom <  _requested_static_archive_bottom ||
      my_archive_requested_top    <= _requested_static_archive_bottom) {
    // Size overflow.
    log_error(cds)("my_archive_requested_bottom = " INTPTR_FORMAT, p2i(my_archive_requested_bottom));
    log_error(cds)("my_archive_requested_top    = " INTPTR_FORMAT, p2i(my_archive_requested_top));
    log_error(cds)("SharedBaseAddress (" INTPTR_FORMAT ") is too high. "
                   "Please rerun java -Xshare:dump with a lower value", p2i(_requested_static_archive_bottom));
    MetaspaceShared::unrecoverable_writing_error();
  }

  if (CDSConfig::is_dumping_static_archive()) {
    // We don't want any valid object to be at the very bottom of the archive.
    // See ArchivePtrMarker::mark_pointer().
    rw_region()->allocate(16);
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
  if (RegeneratedClasses::has_been_regenerated(src_obj)) {
    // No need to copy it. We will later relocate it to point to the regenerated klass/method.
    return false;
  }
  remember_embedded_pointer_in_enclosing_obj(ref);

  FollowMode follow_mode = get_follow_mode(ref);
  SourceObjInfo src_info(ref, read_only, follow_mode);
  bool created;
  SourceObjInfo* p = _src_obj_table.put_if_absent(src_obj, src_info, &created);
  if (created) {
    if (_src_obj_table.maybe_grow()) {
      log_info(cds, hashtables)("Expanded _src_obj_table table to %d", _src_obj_table.table_size());
    }
  }

#ifdef ASSERT
  if (ref->msotype() == MetaspaceObj::MethodType) {
    Method* m = (Method*)ref->obj();
    assert(!RegeneratedClasses::has_been_regenerated((address)m->method_holder()),
           "Should not archive methods in a class that has been regenerated");
  }
#endif

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
  log_info(cds)("Gathering all archivable objects ... ");
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
    if (MetaspaceShared::is_shared_static(bottom)) {
      // The bottom class is in the static archive so it's clearly not excluded.
      assert(CDSConfig::is_dumping_dynamic_archive(), "sanity");
      return false;
    } else if (bottom->is_instance_klass()) {
      return SystemDictionaryShared::is_excluded_class(InstanceKlass::cast(bottom));
    }
  }

  return false;
}

ArchiveBuilder::FollowMode ArchiveBuilder::get_follow_mode(MetaspaceClosure::Ref *ref) {
  address obj = ref->obj();
  if (MetaspaceShared::is_in_shared_metaspace(obj)) {
    // Don't dump existing shared metadata again.
    return point_to_it;
  } else if (ref->msotype() == MetaspaceObj::MethodDataType ||
             ref->msotype() == MetaspaceObj::MethodCountersType) {
    return set_to_null;
  } else {
    if (ref->msotype() == MetaspaceObj::ClassType) {
      Klass* klass = (Klass*)ref->obj();
      assert(klass->is_klass(), "must be");
      if (is_excluded(klass)) {
        ResourceMark rm;
        log_debug(cds, dynamic)("Skipping class (excluded): %s", klass->external_name());
        return set_to_null;
      }
    }

    return make_a_copy;
  }
}

void ArchiveBuilder::start_dump_region(DumpRegion* next) {
  address bottom = _last_verified_top;
  address top = (address)(current_dump_region()->top());
  _other_region_used_bytes += size_t(top - bottom);

  current_dump_region()->pack(next);
  _current_dump_region = next;
  _num_dump_regions_used ++;

  _last_verified_top = (address)(current_dump_region()->top());
}

void ArchiveBuilder::verify_estimate_size(size_t estimate, const char* which) {
  address bottom = _last_verified_top;
  address top = (address)(current_dump_region()->top());
  size_t used = size_t(top - bottom) + _other_region_used_bytes;
  int diff = int(estimate) - int(used);

  log_info(cds)("%s estimate = " SIZE_FORMAT " used = " SIZE_FORMAT "; diff = %d bytes", which, estimate, used, diff);
  assert(diff >= 0, "Estimate is too small");

  _last_verified_top = top;
  _other_region_used_bytes = 0;
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
  log_info(cds)("Allocating RW objects ... ");
  make_shallow_copies(&_rw_region, &_rw_src_objs);

#if INCLUDE_CDS_JAVA_HEAP
  if (CDSConfig::is_dumping_full_module_graph()) {
    // Archive the ModuleEntry's and PackageEntry's of the 3 built-in loaders
    char* start = rw_region()->top();
    ClassLoaderDataShared::allocate_archived_tables();
    alloc_stats()->record_modules(rw_region()->top() - start, /*read_only*/false);
  }
#endif
}

void ArchiveBuilder::dump_ro_metadata() {
  ResourceMark rm;
  log_info(cds)("Allocating RO objects ... ");

  start_dump_region(&_ro_region);
  make_shallow_copies(&_ro_region, &_ro_src_objs);

#if INCLUDE_CDS_JAVA_HEAP
  if (CDSConfig::is_dumping_full_module_graph()) {
    char* start = ro_region()->top();
    ClassLoaderDataShared::init_archived_tables();
    alloc_stats()->record_modules(ro_region()->top() - start, /*read_only*/true);
  }
#endif

  RegeneratedClasses::record_regenerated_objects();
}

void ArchiveBuilder::make_shallow_copies(DumpRegion *dump_region,
                                         const ArchiveBuilder::SourceObjList* src_objs) {
  for (int i = 0; i < src_objs->objs()->length(); i++) {
    make_shallow_copy(dump_region, src_objs->objs()->at(i));
  }
  log_info(cds)("done (%d objects)", src_objs->objs()->length());
}

void ArchiveBuilder::make_shallow_copy(DumpRegion *dump_region, SourceObjInfo* src_info) {
  address src = src_info->source_addr();
  int bytes = src_info->size_in_bytes();
  char* dest;
  char* oldtop;
  char* newtop;

  oldtop = dump_region->top();
  if (src_info->msotype() == MetaspaceObj::ClassType) {
    // Save a pointer immediate in front of an InstanceKlass, so
    // we can do a quick lookup from InstanceKlass* -> RunTimeClassInfo*
    // without building another hashtable. See RunTimeClassInfo::get_for()
    // in systemDictionaryShared.cpp.
    Klass* klass = (Klass*)src;
    if (klass->is_instance_klass()) {
      SystemDictionaryShared::validate_before_archiving(InstanceKlass::cast(klass));
      dump_region->allocate(sizeof(address));
    }
  }
  dest = dump_region->allocate(bytes);
  newtop = dump_region->top();

  memcpy(dest, src, bytes);

  // Update the hash of buffered sorted symbols for static dump so that the symbols have deterministic contents
  if (CDSConfig::is_dumping_static_archive() && (src_info->msotype() == MetaspaceObj::SymbolType)) {
    Symbol* buffered_symbol = (Symbol*)dest;
    assert(((Symbol*)src)->is_permanent(), "archived symbols must be permanent");
    buffered_symbol->update_identity_hash();
  }

  {
    bool created;
    _buffered_to_src_table.put_if_absent((address)dest, src, &created);
    assert(created, "must be");
    if (_buffered_to_src_table.maybe_grow()) {
      log_info(cds, hashtables)("Expanded _buffered_to_src_table table to %d", _buffered_to_src_table.table_size());
    }
  }

  intptr_t* archived_vtable = CppVtables::get_archived_vtable(src_info->msotype(), (address)dest);
  if (archived_vtable != nullptr) {
    *(address*)dest = (address)archived_vtable;
    ArchivePtrMarker::mark_pointer((address*)dest);
  }

  log_trace(cds)("Copy: " PTR_FORMAT " ==> " PTR_FORMAT " %d", p2i(src), p2i(dest), bytes);
  src_info->set_buffered_addr((address)dest);

  _alloc_stats.record(src_info->msotype(), int(newtop - oldtop), src_info->read_only());
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
  log_info(cds)("Relocating embedded pointers in core regions ... ");
  relocate_embedded_pointers(&_rw_src_objs);
  relocate_embedded_pointers(&_ro_src_objs);
}

void ArchiveBuilder::make_klasses_shareable() {
  int num_instance_klasses = 0;
  int num_boot_klasses = 0;
  int num_platform_klasses = 0;
  int num_app_klasses = 0;
  int num_hidden_klasses = 0;
  int num_unlinked_klasses = 0;
  int num_unregistered_klasses = 0;
  int num_obj_array_klasses = 0;
  int num_type_array_klasses = 0;

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
    const char* hidden = "";
    const char* generated = "";
    Klass* k = get_buffered_addr(klasses()->at(i));
    k->remove_java_mirror();
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
      num_instance_klasses ++;
      InstanceKlass* ik = InstanceKlass::cast(k);
      if (ik->is_shared_boot_class()) {
        type = "boot";
        num_boot_klasses ++;
      } else if (ik->is_shared_platform_class()) {
        type = "plat";
        num_platform_klasses ++;
      } else if (ik->is_shared_app_class()) {
        type = "app";
        num_app_klasses ++;
      } else {
        assert(ik->is_shared_unregistered_class(), "must be");
        type = "unreg";
        num_unregistered_klasses ++;
      }

      if (!ik->is_linked()) {
        num_unlinked_klasses ++;
        unlinked = " ** unlinked";
      }

      if (ik->is_hidden()) {
        num_hidden_klasses ++;
        hidden = " ** hidden";
      }

      if (ik->is_generated_shared_class()) {
        generated = " ** generated";
      }
      MetaspaceShared::rewrite_nofast_bytecodes_and_calculate_fingerprints(Thread::current(), ik);
      ik->remove_unshareable_info();
    }

    if (log_is_enabled(Debug, cds, class)) {
      ResourceMark rm;
      log_debug(cds, class)("klasses[%5d] = " PTR_FORMAT " %-5s %s%s%s%s", i,
                            p2i(to_requested(k)), type, k->external_name(),
                            hidden, unlinked, generated);
    }
  }

  log_info(cds)("Number of classes %d", num_instance_klasses + num_obj_array_klasses + num_type_array_klasses);
  log_info(cds)("    instance classes   = %5d", num_instance_klasses);
  log_info(cds)("      boot             = %5d", num_boot_klasses);
  log_info(cds)("      app              = %5d", num_app_klasses);
  log_info(cds)("      platform         = %5d", num_platform_klasses);
  log_info(cds)("      unregistered     = %5d", num_unregistered_klasses);
  log_info(cds)("      (hidden)         = %5d", num_hidden_klasses);
  log_info(cds)("      (unlinked)       = %5d", num_unlinked_klasses);
  log_info(cds)("    obj array classes  = %5d", num_obj_array_klasses);
  log_info(cds)("    type array classes = %5d", num_type_array_klasses);
  log_info(cds)("               symbols = %5d", _symbols->length());

  DynamicArchive::make_array_klasses_shareable();
}

void ArchiveBuilder::serialize_dynamic_archivable_items(SerializeClosure* soc) {
  SymbolTable::serialize_shared_table_header(soc, false);
  SystemDictionaryShared::serialize_dictionary_headers(soc, false);
  DynamicArchive::serialize_array_klasses(soc);
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

#if INCLUDE_CDS_JAVA_HEAP
narrowKlass ArchiveBuilder::get_requested_narrow_klass(Klass* k) {
  assert(CDSConfig::is_dumping_heap(), "sanity");
  k = get_buffered_klass(k);
  Klass* requested_k = to_requested(k);
  address narrow_klass_base = _requested_static_archive_bottom; // runtime encoding base == runtime mapping start
  const int narrow_klass_shift = ArchiveHeapWriter::precomputed_narrow_klass_shift;
  return CompressedKlassPointers::encode_not_null(requested_k, narrow_klass_base, narrow_klass_shift);
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
    log_debug(cds)("Relocating archive from [" INTPTR_FORMAT " - " INTPTR_FORMAT "] to "
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


void ArchiveBuilder::relocate_to_requested() {
  ro_region()->pack();

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

// Write detailed info to a mapfile to analyze contents of the archive.
// static dump:
//   java -Xshare:dump -Xlog:cds+map=trace:file=cds.map:none:filesize=0
// dynamic dump:
//   java -cp MyApp.jar -XX:ArchiveClassesAtExit=MyApp.jsa \
//        -Xlog:cds+map=trace:file=cds.map:none:filesize=0 MyApp
//
// We need to do some address translation because the buffers used at dump time may be mapped to
// a different location at runtime. At dump time, the buffers may be at arbitrary locations
// picked by the OS. At runtime, we try to map at a fixed location (SharedBaseAddress). For
// consistency, we log everything using runtime addresses.
class ArchiveBuilder::CDSMapLogger : AllStatic {
  static intx buffer_to_runtime_delta() {
    // Translate the buffers used by the RW/RO regions to their eventual (requested) locations
    // at runtime.
    return ArchiveBuilder::current()->buffer_to_requested_delta();
  }

  // rw/ro regions only
  static void log_metaspace_region(const char* name, DumpRegion* region,
                                   const ArchiveBuilder::SourceObjList* src_objs) {
    address region_base = address(region->base());
    address region_top  = address(region->top());
    log_region(name, region_base, region_top, region_base + buffer_to_runtime_delta());
    log_metaspace_objects(region, src_objs);
  }

#define _LOG_PREFIX PTR_FORMAT ": @@ %-17s %d"

  static void log_klass(Klass* k, address runtime_dest, const char* type_name, int bytes, Thread* current) {
    ResourceMark rm(current);
    log_debug(cds, map)(_LOG_PREFIX " %s",
                        p2i(runtime_dest), type_name, bytes, k->external_name());
  }
  static void log_method(Method* m, address runtime_dest, const char* type_name, int bytes, Thread* current) {
    ResourceMark rm(current);
    log_debug(cds, map)(_LOG_PREFIX " %s",
                        p2i(runtime_dest), type_name, bytes,  m->external_name());
  }

  // rw/ro regions only
  static void log_metaspace_objects(DumpRegion* region, const ArchiveBuilder::SourceObjList* src_objs) {
    address last_obj_base = address(region->base());
    address last_obj_end  = address(region->base());
    address region_end    = address(region->end());
    Thread* current = Thread::current();
    for (int i = 0; i < src_objs->objs()->length(); i++) {
      SourceObjInfo* src_info = src_objs->at(i);
      address src = src_info->source_addr();
      address dest = src_info->buffered_addr();
      log_as_hex(last_obj_base, dest, last_obj_base + buffer_to_runtime_delta());
      address runtime_dest = dest + buffer_to_runtime_delta();
      int bytes = src_info->size_in_bytes();

      MetaspaceObj::Type type = src_info->msotype();
      const char* type_name = MetaspaceObj::type_name(type);

      switch (type) {
      case MetaspaceObj::ClassType:
        log_klass((Klass*)src, runtime_dest, type_name, bytes, current);
        break;
      case MetaspaceObj::ConstantPoolType:
        log_klass(((ConstantPool*)src)->pool_holder(),
                    runtime_dest, type_name, bytes, current);
        break;
      case MetaspaceObj::ConstantPoolCacheType:
        log_klass(((ConstantPoolCache*)src)->constant_pool()->pool_holder(),
                    runtime_dest, type_name, bytes, current);
        break;
      case MetaspaceObj::MethodType:
        log_method((Method*)src, runtime_dest, type_name, bytes, current);
        break;
      case MetaspaceObj::ConstMethodType:
        log_method(((ConstMethod*)src)->method(), runtime_dest, type_name, bytes, current);
        break;
      case MetaspaceObj::SymbolType:
        {
          ResourceMark rm(current);
          Symbol* s = (Symbol*)src;
          log_debug(cds, map)(_LOG_PREFIX " %s", p2i(runtime_dest), type_name, bytes,
                              s->as_quoted_ascii());
        }
        break;
      default:
        log_debug(cds, map)(_LOG_PREFIX, p2i(runtime_dest), type_name, bytes);
        break;
      }

      last_obj_base = dest;
      last_obj_end  = dest + bytes;
    }

    log_as_hex(last_obj_base, last_obj_end, last_obj_base + buffer_to_runtime_delta());
    if (last_obj_end < region_end) {
      log_debug(cds, map)(PTR_FORMAT ": @@ Misc data " SIZE_FORMAT " bytes",
                          p2i(last_obj_end + buffer_to_runtime_delta()),
                          size_t(region_end - last_obj_end));
      log_as_hex(last_obj_end, region_end, last_obj_end + buffer_to_runtime_delta());
    }
  }

#undef _LOG_PREFIX

  // Log information about a region, whose address at dump time is [base .. top). At
  // runtime, this region will be mapped to requested_base. requested_base is 0 if this
  // region will be mapped at os-selected addresses (such as the bitmap region), or will
  // be accessed with os::read (the header).
  //
  // Note: across -Xshare:dump runs, base may be different, but requested_base should
  // be the same as the archive contents should be deterministic.
  static void log_region(const char* name, address base, address top, address requested_base) {
    size_t size = top - base;
    base = requested_base;
    top = requested_base + size;
    log_info(cds, map)("[%-18s " PTR_FORMAT " - " PTR_FORMAT " " SIZE_FORMAT_W(9) " bytes]",
                       name, p2i(base), p2i(top), size);
  }

#if INCLUDE_CDS_JAVA_HEAP
  static void log_heap_region(ArchiveHeapInfo* heap_info) {
    MemRegion r = heap_info->buffer_region();
    address start = address(r.start()); // start of the current oop inside the buffer
    address end = address(r.end());
    log_region("heap", start, end, ArchiveHeapWriter::buffered_addr_to_requested_addr(start));

    LogStreamHandle(Info, cds, map) st;

    while (start < end) {
      size_t byte_size;
      oop source_oop = ArchiveHeapWriter::buffered_addr_to_source_obj(start);
      address requested_start = ArchiveHeapWriter::buffered_addr_to_requested_addr(start);
      st.print(PTR_FORMAT ": @@ Object ", p2i(requested_start));

      if (source_oop != nullptr) {
        // This is a regular oop that got archived.
        print_oop_with_requested_addr_cr(&st, source_oop, false);
        byte_size = source_oop->size() * BytesPerWord;
      } else if (start == ArchiveHeapWriter::buffered_heap_roots_addr()) {
        // HeapShared::roots() is copied specially, so it doesn't exist in
        // ArchiveHeapWriter::BufferOffsetToSourceObjectTable.
        // See ArchiveHeapWriter::copy_roots_to_buffer().
        st.print_cr("HeapShared::roots[%d]", HeapShared::pending_roots()->length());
        byte_size = ArchiveHeapWriter::heap_roots_word_size() * BytesPerWord;
      } else if ((byte_size = ArchiveHeapWriter::get_filler_size_at(start)) > 0) {
        // We have a filler oop, which also does not exist in BufferOffsetToSourceObjectTable.
        st.print_cr("filler " SIZE_FORMAT " bytes", byte_size);
      } else {
        ShouldNotReachHere();
      }

      address oop_end = start + byte_size;
      log_as_hex(start, oop_end, requested_start, /*is_heap=*/true);

      if (source_oop != nullptr) {
        log_oop_details(heap_info, source_oop, /*buffered_addr=*/start);
      } else if (start == ArchiveHeapWriter::buffered_heap_roots_addr()) {
        log_heap_roots();
      }
      start = oop_end;
    }
  }

  // ArchivedFieldPrinter is used to print the fields of archived objects. We can't
  // use _source_obj->print_on(), because we want to print the oop fields
  // in _source_obj with their requested addresses using print_oop_with_requested_addr_cr().
  class ArchivedFieldPrinter : public FieldClosure {
    ArchiveHeapInfo* _heap_info;
    outputStream* _st;
    oop _source_obj;
    address _buffered_addr;
  public:
    ArchivedFieldPrinter(ArchiveHeapInfo* heap_info, outputStream* st, oop src_obj, address buffered_addr) :
      _heap_info(heap_info), _st(st), _source_obj(src_obj), _buffered_addr(buffered_addr) {}

    void do_field(fieldDescriptor* fd) {
      _st->print(" - ");
      BasicType ft = fd->field_type();
      switch (ft) {
      case T_ARRAY:
      case T_OBJECT:
        fd->print_on(_st); // print just the name and offset
        print_oop_with_requested_addr_cr(_st, _source_obj->obj_field(fd->offset()));
        break;
      default:
        if (ArchiveHeapWriter::is_marked_as_native_pointer(_heap_info, _source_obj, fd->offset())) {
          print_as_native_pointer(fd);
        } else {
          fd->print_on_for(_st, cast_to_oop(_buffered_addr)); // name, offset, value
          _st->cr();
        }
      }
    }

    void print_as_native_pointer(fieldDescriptor* fd) {
      LP64_ONLY(assert(fd->field_type() == T_LONG, "must be"));
      NOT_LP64 (assert(fd->field_type() == T_INT,  "must be"));

      // We have a field that looks like an integer, but it's actually a pointer to a MetaspaceObj.
      address source_native_ptr = (address)
          LP64_ONLY(_source_obj->long_field(fd->offset()))
          NOT_LP64( _source_obj->int_field (fd->offset()));
      ArchiveBuilder* builder = ArchiveBuilder::current();

      // The value of the native pointer at runtime.
      address requested_native_ptr = builder->to_requested(builder->get_buffered_addr(source_native_ptr));

      // The address of _source_obj at runtime
      oop requested_obj = ArchiveHeapWriter::source_obj_to_requested_obj(_source_obj);
      // The address of this field in the requested space
      assert(requested_obj != nullptr, "Attempting to load field from null oop");
      address requested_field_addr = cast_from_oop<address>(requested_obj) + fd->offset();

      fd->print_on(_st);
      _st->print_cr(PTR_FORMAT " (marked metadata pointer @" PTR_FORMAT " )",
                    p2i(requested_native_ptr), p2i(requested_field_addr));
    }
  };

  // Print the fields of instanceOops, or the elements of arrayOops
  static void log_oop_details(ArchiveHeapInfo* heap_info, oop source_oop, address buffered_addr) {
    LogStreamHandle(Trace, cds, map, oops) st;
    if (st.is_enabled()) {
      Klass* source_klass = source_oop->klass();
      ArchiveBuilder* builder = ArchiveBuilder::current();
      Klass* requested_klass = builder->to_requested(builder->get_buffered_addr(source_klass));

      st.print(" - klass: ");
      source_klass->print_value_on(&st);
      st.print(" " PTR_FORMAT, p2i(requested_klass));
      st.cr();

      if (source_oop->is_typeArray()) {
        TypeArrayKlass::cast(source_klass)->oop_print_elements_on(typeArrayOop(source_oop), &st);
      } else if (source_oop->is_objArray()) {
        objArrayOop source_obj_array = objArrayOop(source_oop);
        for (int i = 0; i < source_obj_array->length(); i++) {
          st.print(" -%4d: ", i);
          print_oop_with_requested_addr_cr(&st, source_obj_array->obj_at(i));
        }
      } else {
        st.print_cr(" - fields (" SIZE_FORMAT " words):", source_oop->size());
        ArchivedFieldPrinter print_field(heap_info, &st, source_oop, buffered_addr);
        InstanceKlass::cast(source_klass)->print_nonstatic_fields(&print_field);
      }
    }
  }

  static void log_heap_roots() {
    LogStreamHandle(Trace, cds, map, oops) st;
    if (st.is_enabled()) {
      for (int i = 0; i < HeapShared::pending_roots()->length(); i++) {
        st.print("roots[%4d]: ", i);
        print_oop_with_requested_addr_cr(&st, HeapShared::pending_roots()->at(i));
      }
    }
  }

  // The output looks like this. The first number is the requested address. The second number is
  // the narrowOop version of the requested address.
  //     0x00000007ffc7e840 (0xfff8fd08) java.lang.Class
  //     0x00000007ffc000f8 (0xfff8001f) [B length: 11
  static void print_oop_with_requested_addr_cr(outputStream* st, oop source_oop, bool print_addr = true) {
    if (source_oop == nullptr) {
      st->print_cr("null");
    } else {
      ResourceMark rm;
      oop requested_obj = ArchiveHeapWriter::source_obj_to_requested_obj(source_oop);
      if (print_addr) {
        st->print(PTR_FORMAT " ", p2i(requested_obj));
      }
      if (UseCompressedOops) {
        st->print("(0x%08x) ", CompressedOops::narrow_oop_value(requested_obj));
      }
      if (source_oop->is_array()) {
        int array_len = arrayOop(source_oop)->length();
        st->print_cr("%s length: %d", source_oop->klass()->external_name(), array_len);
      } else {
        st->print_cr("%s", source_oop->klass()->external_name());
      }
    }
  }
#endif // INCLUDE_CDS_JAVA_HEAP

  // Log all the data [base...top). Pretend that the base address
  // will be mapped to requested_base at run-time.
  static void log_as_hex(address base, address top, address requested_base, bool is_heap = false) {
    assert(top >= base, "must be");

    LogStreamHandle(Trace, cds, map) lsh;
    if (lsh.is_enabled()) {
      int unitsize = sizeof(address);
      if (is_heap && UseCompressedOops) {
        // This makes the compressed oop pointers easier to read, but
        // longs and doubles will be split into two words.
        unitsize = sizeof(narrowOop);
      }
      os::print_hex_dump(&lsh, base, top, unitsize, 32, requested_base);
    }
  }

  static void log_header(FileMapInfo* mapinfo) {
    LogStreamHandle(Info, cds, map) lsh;
    if (lsh.is_enabled()) {
      mapinfo->print(&lsh);
    }
  }

public:
  static void log(ArchiveBuilder* builder, FileMapInfo* mapinfo,
                  ArchiveHeapInfo* heap_info,
                  char* bitmap, size_t bitmap_size_in_bytes) {
    log_info(cds, map)("%s CDS archive map for %s", CDSConfig::is_dumping_static_archive() ? "Static" : "Dynamic", mapinfo->full_path());

    address header = address(mapinfo->header());
    address header_end = header + mapinfo->header()->header_size();
    log_region("header", header, header_end, 0);
    log_header(mapinfo);
    log_as_hex(header, header_end, 0);

    DumpRegion* rw_region = &builder->_rw_region;
    DumpRegion* ro_region = &builder->_ro_region;

    log_metaspace_region("rw region", rw_region, &builder->_rw_src_objs);
    log_metaspace_region("ro region", ro_region, &builder->_ro_src_objs);

    address bitmap_end = address(bitmap + bitmap_size_in_bytes);
    log_region("bitmap", address(bitmap), bitmap_end, 0);
    log_as_hex((address)bitmap, bitmap_end, 0);

#if INCLUDE_CDS_JAVA_HEAP
    if (heap_info->is_used()) {
      log_heap_region(heap_info);
    }
#endif

    log_info(cds, map)("[End of CDS archive map]");
  }
}; // end ArchiveBuilder::CDSMapLogger

void ArchiveBuilder::print_stats() {
  _alloc_stats.print_stats(int(_ro_region.used()), int(_rw_region.used()));
}

void ArchiveBuilder::write_archive(FileMapInfo* mapinfo, ArchiveHeapInfo* heap_info) {
  // Make sure NUM_CDS_REGIONS (exported in cds.h) agrees with
  // MetaspaceShared::n_regions (internal to hotspot).
  assert(NUM_CDS_REGIONS == MetaspaceShared::n_regions, "sanity");

  write_region(mapinfo, MetaspaceShared::rw, &_rw_region, /*read_only=*/false,/*allow_exec=*/false);
  write_region(mapinfo, MetaspaceShared::ro, &_ro_region, /*read_only=*/true, /*allow_exec=*/false);

  // Split pointer map into read-write and read-only bitmaps
  ArchivePtrMarker::initialize_rw_ro_maps(&_rw_ptrmap, &_ro_ptrmap);

  size_t bitmap_size_in_bytes;
  char* bitmap = mapinfo->write_bitmap_region(ArchivePtrMarker::rw_ptrmap(), ArchivePtrMarker::ro_ptrmap(), heap_info,
                                              bitmap_size_in_bytes);

  if (heap_info->is_used()) {
    _total_heap_region_size = mapinfo->write_heap_region(heap_info);
  }

  print_region_stats(mapinfo, heap_info);

  mapinfo->set_requested_base((char*)MetaspaceShared::requested_base_address());
  mapinfo->set_header_crc(mapinfo->compute_header_crc());
  // After this point, we should not write any data into mapinfo->header() since this
  // would corrupt its checksum we have calculated before.
  mapinfo->write_header();
  mapinfo->close();

  if (log_is_enabled(Info, cds)) {
    print_stats();
  }

  if (log_is_enabled(Info, cds, map)) {
    CDSMapLogger::log(this, mapinfo, heap_info,
                      bitmap, bitmap_size_in_bytes);
  }
  CDS_JAVA_HEAP_ONLY(HeapShared::destroy_archived_object_cache());
  FREE_C_HEAP_ARRAY(char, bitmap);
}

void ArchiveBuilder::write_region(FileMapInfo* mapinfo, int region_idx, DumpRegion* dump_region, bool read_only,  bool allow_exec) {
  mapinfo->write_region(region_idx, dump_region->base(), dump_region->used(), read_only, allow_exec);
}

void ArchiveBuilder::print_region_stats(FileMapInfo *mapinfo, ArchiveHeapInfo* heap_info) {
  // Print statistics of all the regions
  const size_t bitmap_used = mapinfo->region_at(MetaspaceShared::bm)->used();
  const size_t bitmap_reserved = mapinfo->region_at(MetaspaceShared::bm)->used_aligned();
  const size_t total_reserved = _ro_region.reserved()  + _rw_region.reserved() +
                                bitmap_reserved +
                                _total_heap_region_size;
  const size_t total_bytes = _ro_region.used()  + _rw_region.used() +
                             bitmap_used +
                             _total_heap_region_size;
  const double total_u_perc = percent_of(total_bytes, total_reserved);

  _rw_region.print(total_reserved);
  _ro_region.print(total_reserved);

  print_bitmap_region_stats(bitmap_used, total_reserved);

  if (heap_info->is_used()) {
    print_heap_region_stats(heap_info, total_reserved);
  }

  log_debug(cds)("total   : " SIZE_FORMAT_W(9) " [100.0%% of total] out of " SIZE_FORMAT_W(9) " bytes [%5.1f%% used]",
                 total_bytes, total_reserved, total_u_perc);
}

void ArchiveBuilder::print_bitmap_region_stats(size_t size, size_t total_size) {
  log_debug(cds)("bm space: " SIZE_FORMAT_W(9) " [ %4.1f%% of total] out of " SIZE_FORMAT_W(9) " bytes [100.0%% used]",
                 size, size/double(total_size)*100.0, size);
}

void ArchiveBuilder::print_heap_region_stats(ArchiveHeapInfo *info, size_t total_size) {
  char* start = info->buffer_start();
  size_t size = info->buffer_byte_size();
  char* top = start + size;
  log_debug(cds)("hp space: " SIZE_FORMAT_W(9) " [ %4.1f%% of total] out of " SIZE_FORMAT_W(9) " bytes [100.0%% used] at " INTPTR_FORMAT,
                     size, size/double(total_size)*100.0, size, p2i(start));
}

void ArchiveBuilder::report_out_of_space(const char* name, size_t needed_bytes) {
  // This is highly unlikely to happen on 64-bits because we have reserved a 4GB space.
  // On 32-bit we reserve only 256MB so you could run out of space with 100,000 classes
  // or so.
  _rw_region.print_out_of_space_msg(name, needed_bytes);
  _ro_region.print_out_of_space_msg(name, needed_bytes);

  log_error(cds)("Unable to allocate from '%s' region: Please reduce the number of shared classes.", name);
  MetaspaceShared::unrecoverable_writing_error();
}


#ifndef PRODUCT
void ArchiveBuilder::assert_is_vm_thread() {
  assert(Thread::current()->is_VM_thread(), "ArchiveBuilder should be used only inside the VMThread");
}
#endif
