/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionaryShared.hpp"
#include "logging/log.hpp"
#include "logging/logMessage.hpp"
#include "memory/archiveBuilder.hpp"
#include "memory/archiveUtils.hpp"
#include "memory/dumpAllocStats.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oopHandle.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/hashtable.inline.hpp"

ArchiveBuilder* ArchiveBuilder::_singleton = NULL;

ArchiveBuilder::OtherROAllocMark::~OtherROAllocMark() {
  char* newtop = ArchiveBuilder::singleton()->_ro_region->top();
  ArchiveBuilder::alloc_stats()->record_other_type(int(newtop - _oldtop), true);
}

ArchiveBuilder::SourceObjList::SourceObjList() : _ptrmap(16 * K) {
  _total_bytes = 0;
  _objs = new (ResourceObj::C_HEAP, mtClassShared) GrowableArray<SourceObjInfo*>(128 * K, mtClassShared);
}

ArchiveBuilder::SourceObjList::~SourceObjList() {
  delete _objs;
}

void ArchiveBuilder::SourceObjList::append(MetaspaceClosure::Ref* enclosing_ref, SourceObjInfo* src_info) {
  // Save this source object for copying
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
  // so that we can copy/relocate it later. E.g., if we have
  //    class Foo { intx scala; Bar* ptr; }
  //    Foo *f = 0x100;
  // To mark the f->ptr pointer on 64-bit platform, this function is called with
  //    src_info()->obj() == 0x100
  //    ref->addr() == 0x108
  address src_obj = src_info->obj();
  address* field_addr = ref->addr();
  assert(src_info->ptrmap_start() < _total_bytes, "sanity");
  assert(src_info->ptrmap_end() <= _total_bytes, "sanity");
  assert(*field_addr != NULL, "should have checked");

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
  address _dumped_obj;
  BitMap::idx_t _start_idx;
public:
  RelocateEmbeddedPointers(ArchiveBuilder* builder, address dumped_obj, BitMap::idx_t start_idx) :
    _builder(builder), _dumped_obj(dumped_obj), _start_idx(start_idx) {}

  bool do_bit(BitMap::idx_t bit_offset) {
    uintx FLAG_MASK = 0x03; // See comments around MetaspaceClosure::FLAG_MASK
    size_t field_offset = size_t(bit_offset - _start_idx) * sizeof(address);
    address* ptr_loc = (address*)(_dumped_obj + field_offset);

    uintx old_p_and_bits = (uintx)(*ptr_loc);
    uintx flag_bits = (old_p_and_bits & FLAG_MASK);
    address old_p = (address)(old_p_and_bits & (~FLAG_MASK));
    address new_p = _builder->get_dumped_addr(old_p);
    uintx new_p_and_bits = ((uintx)new_p) | flag_bits;

    log_trace(cds)("Ref: [" PTR_FORMAT "] -> " PTR_FORMAT " => " PTR_FORMAT,
                   p2i(ptr_loc), p2i(old_p), p2i(new_p));

    ArchivePtrMarker::set_and_mark_pointer(ptr_loc, (address)(new_p_and_bits));
    return true; // keep iterating the bitmap
  }
};

void ArchiveBuilder::SourceObjList::relocate(int i, ArchiveBuilder* builder) {
  SourceObjInfo* src_info = objs()->at(i);
  assert(src_info->should_copy(), "must be");
  BitMap::idx_t start = BitMap::idx_t(src_info->ptrmap_start()); // inclusive
  BitMap::idx_t end = BitMap::idx_t(src_info->ptrmap_end());     // exclusive

  RelocateEmbeddedPointers relocator(builder, src_info->dumped_addr(), start);
  _ptrmap.iterate(&relocator, start, end);
}

ArchiveBuilder::ArchiveBuilder(DumpRegion* rw_region, DumpRegion* ro_region)
  : _rw_src_objs(), _ro_src_objs(), _src_obj_table(INITIAL_TABLE_SIZE) {
  assert(_singleton == NULL, "must be");
  _singleton = this;

  _klasses = new (ResourceObj::C_HEAP, mtClassShared) GrowableArray<Klass*>(4 * K, mtClassShared);
  _symbols = new (ResourceObj::C_HEAP, mtClassShared) GrowableArray<Symbol*>(256 * K, mtClassShared);
  _special_refs = new (ResourceObj::C_HEAP, mtClassShared) GrowableArray<SpecialRefInfo>(24 * K, mtClassShared);

  _num_instance_klasses = 0;
  _num_obj_array_klasses = 0;
  _num_type_array_klasses = 0;
  _alloc_stats = new (ResourceObj::C_HEAP, mtClassShared) DumpAllocStats;

  _rw_region = rw_region;
  _ro_region = ro_region;

  _estimated_metsapceobj_bytes = 0;
}

ArchiveBuilder::~ArchiveBuilder() {
  assert(_singleton == this, "must be");
  _singleton = NULL;

  clean_up_src_obj_table();

  delete _klasses;
  delete _symbols;
  delete _special_refs;
  delete _alloc_stats;
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
  if (ref->obj() == NULL) {
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
      if (klass->is_instance_klass()) {
        _num_instance_klasses ++;
      } else if (klass->is_objArray_klass()) {
        _num_obj_array_klasses ++;
      } else {
        assert(klass->is_typeArray_klass(), "sanity");
        _num_type_array_klasses ++;
      }
    }
    _estimated_metsapceobj_bytes += BytesPerWord; // See RunTimeSharedClassInfo::get_for()
  } else if (ref->msotype() == MetaspaceObj::SymbolType) {
    _symbols->append((Symbol*)ref->obj());
  }

  int bytes = ref->size() * BytesPerWord;
  _estimated_metsapceobj_bytes += bytes;

  return true; // recurse
}

void ArchiveBuilder::gather_klasses_and_symbols() {
  ResourceMark rm;
  log_info(cds)("Gathering classes and symbols ... ");
  GatherKlassesAndSymbols doit(this);
  iterate_roots(&doit, /*is_relocating_pointers=*/false);
  doit.finish();

  log_info(cds)("Number of classes %d", _num_instance_klasses + _num_obj_array_klasses + _num_type_array_klasses);
  log_info(cds)("    instance classes   = %5d", _num_instance_klasses);
  log_info(cds)("    obj array classes  = %5d", _num_obj_array_klasses);
  log_info(cds)("    type array classes = %5d", _num_type_array_klasses);

  if (DumpSharedSpaces) {
    // To ensure deterministic contents in the static archive, we need to ensure that
    // we iterate the MetsapceObjs in a deterministic order. It doesn't matter where
    // the MetsapceObjs are located originally, as they are copied sequentially into
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
    sort_symbols_and_fix_hash();
    sort_klasses();
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

void ArchiveBuilder::sort_symbols_and_fix_hash() {
  log_info(cds)("Sorting symbols and fixing identity hash ... ");
  os::init_random(0x12345678);
  _symbols->sort(compare_symbols_by_address);
  for (int i = 0; i < _symbols->length(); i++) {
    assert(_symbols->at(i)->is_permanent(), "archived symbols must be permanent");
    _symbols->at(i)->update_identity_hash();
  }
}

int ArchiveBuilder::compare_klass_by_name(Klass** a, Klass** b) {
  return a[0]->name()->fast_compare(b[0]->name());
}

void ArchiveBuilder::sort_klasses() {
  log_info(cds)("Sorting classes ... ");
  _klasses->sort(compare_klass_by_name);
}

void ArchiveBuilder::iterate_sorted_roots(MetaspaceClosure* it, bool is_relocating_pointers) {
  int i;

  int num_symbols = _symbols->length();
  for (i = 0; i < num_symbols; i++) {
    it->push(&_symbols->at(i));
  }

  int num_klasses = _klasses->length();
  for (i = 0; i < num_klasses; i++) {
    it->push(&_klasses->at(i));
  }

  iterate_roots(it, is_relocating_pointers);
}

class GatherSortedSourceObjs : public MetaspaceClosure {
  ArchiveBuilder* _builder;

public:
  GatherSortedSourceObjs(ArchiveBuilder* builder) : _builder(builder) {}

  virtual bool do_ref(Ref* ref, bool read_only) {
    return _builder->gather_one_source_obj(enclosing_ref(), ref, read_only);
  }

  virtual void push_special(SpecialRef type, Ref* ref, intptr_t* p) {
    assert(type == _method_entry_ref, "only special type allowed for now");
    address src_obj = ref->obj();
    size_t field_offset = pointer_delta(p, src_obj,  sizeof(u1));
    _builder->add_special_ref(type, src_obj, field_offset);
  };

  virtual void do_pending_ref(Ref* ref) {
    if (ref->obj() != NULL) {
      _builder->remember_embedded_pointer_in_copied_obj(enclosing_ref(), ref);
    }
  }
};

bool ArchiveBuilder::gather_one_source_obj(MetaspaceClosure::Ref* enclosing_ref,
                                           MetaspaceClosure::Ref* ref, bool read_only) {
  address src_obj = ref->obj();
  if (src_obj == NULL) {
    return false;
  }
  ref->set_keep_after_pushing();
  remember_embedded_pointer_in_copied_obj(enclosing_ref, ref);

  FollowMode follow_mode = get_follow_mode(ref);
  SourceObjInfo src_info(ref, read_only, follow_mode);
  bool created = false;
  SourceObjInfo* p = _src_obj_table.lookup(src_obj);
  if (p == NULL) {
    p = _src_obj_table.add(src_obj, src_info);
    if (_src_obj_table.maybe_grow(MAX_TABLE_SIZE)) {
      log_info(cds, hashtables)("Expanded _src_obj_table table to %d", _src_obj_table.table_size());
    }
    created = true;
  }

  assert(p->read_only() == src_info.read_only(), "must be");

  if (created && src_info.should_copy()) {
    ref->set_user_data((void*)p);
    if (read_only) {
      _ro_src_objs.append(enclosing_ref, p);
    } else {
      _rw_src_objs.append(enclosing_ref, p);
    }
    return true; // Need to recurse into this ref only if we are copying it
  } else {
    return false;
  }
}

void ArchiveBuilder::add_special_ref(MetaspaceClosure::SpecialRef type, address src_obj, size_t field_offset) {
  _special_refs->append(SpecialRefInfo(type, src_obj, field_offset));
}

void ArchiveBuilder::remember_embedded_pointer_in_copied_obj(MetaspaceClosure::Ref* enclosing_ref,
                                                             MetaspaceClosure::Ref* ref) {
  assert(ref->obj() != NULL, "should have checked");

  if (enclosing_ref != NULL) {
    SourceObjInfo* src_info = (SourceObjInfo*)enclosing_ref->user_data();
    if (src_info == NULL) {
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
}

void ArchiveBuilder::gather_source_objs() {
  ResourceMark rm;
  log_info(cds)("Gathering all archivable objects ... ");
  GatherSortedSourceObjs doit(this);
  iterate_sorted_roots(&doit, /*is_relocating_pointers=*/false);
  doit.finish();
}

bool ArchiveBuilder::is_excluded(Klass* klass) {
  if (klass->is_instance_klass()) {
    InstanceKlass* ik = InstanceKlass::cast(klass);
    return SystemDictionaryShared::is_excluded_class(ik);
  } else if (klass->is_objArray_klass()) {
    if (DynamicDumpSharedSpaces) {
      // Don't support archiving of array klasses for now (WHY???)
      return true;
    }
    Klass* bottom = ObjArrayKlass::cast(klass)->bottom_klass();
    if (bottom->is_instance_klass()) {
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
  } else if (ref->msotype() == MetaspaceObj::MethodDataType) {
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

void ArchiveBuilder::dump_rw_region() {
  ResourceMark rm;
  log_info(cds)("Allocating RW objects ... ");
  make_shallow_copies(_rw_region, &_rw_src_objs);
}

void ArchiveBuilder::dump_ro_region() {
  ResourceMark rm;
  log_info(cds)("Allocating RO objects ... ");
  make_shallow_copies(_ro_region, &_ro_src_objs);
}

void ArchiveBuilder::make_shallow_copies(DumpRegion *dump_region,
                                         const ArchiveBuilder::SourceObjList* src_objs) {
  for (int i = 0; i < src_objs->objs()->length(); i++) {
    make_shallow_copy(dump_region, src_objs->objs()->at(i));
  }
  log_info(cds)("done (%d objects)", src_objs->objs()->length());
}

void ArchiveBuilder::make_shallow_copy(DumpRegion *dump_region, SourceObjInfo* src_info) {
  MetaspaceClosure::Ref* ref = src_info->ref();
  address src = ref->obj();
  int bytes = src_info->size_in_bytes();
  char* dest;
  size_t alignment = BytesPerWord;
  char* oldtop;
  char* newtop;

  oldtop = dump_region->top();
  if (ref->msotype() == MetaspaceObj::ClassType) {
    // Save a pointer immediate in front of an InstanceKlass, so
    // we can do a quick lookup from InstanceKlass* -> RunTimeSharedClassInfo*
    // without building another hashtable. See RunTimeSharedClassInfo::get_for()
    // in systemDictionaryShared.cpp.
    Klass* klass = (Klass*)src;
    if (klass->is_instance_klass()) {
      SystemDictionaryShared::validate_before_archiving(InstanceKlass::cast(klass));
      dump_region->allocate(sizeof(address), BytesPerWord);
    }
  }
  dest = dump_region->allocate(bytes, alignment);
  newtop = dump_region->top();

  memcpy(dest, src, bytes);

  intptr_t* archived_vtable = MetaspaceShared::get_archived_cpp_vtable(ref->msotype(), (address)dest);
  if (archived_vtable != NULL) {
    *(address*)dest = (address)archived_vtable;
    ArchivePtrMarker::mark_pointer((address*)dest);
  }

  log_trace(cds)("Copy: " PTR_FORMAT " ==> " PTR_FORMAT " %d", p2i(src), p2i(dest), bytes);
  src_info->set_dumped_addr((address)dest);

  _alloc_stats->record(ref->msotype(), int(newtop - oldtop), src_info->read_only());
}

address ArchiveBuilder::get_dumped_addr(address src_obj) const {
  SourceObjInfo* p = _src_obj_table.lookup(src_obj);
  assert(p != NULL, "must be");

  return p->dumped_addr();
}

void ArchiveBuilder::relocate_embedded_pointers(ArchiveBuilder::SourceObjList* src_objs) {
  for (int i = 0; i < src_objs->objs()->length(); i++) {
    src_objs->relocate(i, this);
  }
}

void ArchiveBuilder::update_special_refs() {
  for (int i = 0; i < _special_refs->length(); i++) {
    SpecialRefInfo s = _special_refs->at(i);
    size_t field_offset = s.field_offset();
    address src_obj = s.src_obj();
    address dst_obj = get_dumped_addr(src_obj);
    intptr_t* src_p = (intptr_t*)(src_obj + field_offset);
    intptr_t* dst_p = (intptr_t*)(dst_obj + field_offset);
    assert(s.type() == MetaspaceClosure::_method_entry_ref, "only special type allowed for now");

    assert(*src_p == *dst_p, "must be a copy");
    ArchivePtrMarker::mark_pointer((address*)dst_p);
  }
}

class RefRelocator: public MetaspaceClosure {
  ArchiveBuilder* _builder;

public:
  RefRelocator(ArchiveBuilder* builder) : _builder(builder) {}

  virtual bool do_ref(Ref* ref, bool read_only) {
    if (ref->not_null()) {
      ref->update(_builder->get_dumped_addr(ref->obj()));
      ArchivePtrMarker::mark_pointer(ref->addr());
    }
    return false; // Do not recurse.
  }
};

void ArchiveBuilder::relocate_roots() {
  ResourceMark rm;
  RefRelocator doit(this);
  iterate_sorted_roots(&doit, /*is_relocating_pointers=*/true);
  doit.finish();
}

void ArchiveBuilder::relocate_pointers() {
  log_info(cds)("Relocating embedded pointers ... ");
  relocate_embedded_pointers(&_rw_src_objs);
  relocate_embedded_pointers(&_ro_src_objs);
  update_special_refs();

  log_info(cds)("Relocating external roots ... ");
  relocate_roots();

  log_info(cds)("done");
}

// We must relocate the System::_well_known_klasses only after we have copied the
// java objects in during dump_java_heap_objects(): during the object copy, we operate on
// old objects which assert that their klass is the original klass.
void ArchiveBuilder::relocate_well_known_klasses() {
  log_info(cds)("Relocating SystemDictionary::_well_known_klasses[] ... ");
  ResourceMark rm;
  RefRelocator doit(this);
  SystemDictionary::well_known_klasses_do(&doit);
}

void ArchiveBuilder::print_stats(int ro_all, int rw_all, int mc_all) {
  _alloc_stats->print_stats(ro_all, rw_all, mc_all);
}

void ArchiveBuilder::clean_up_src_obj_table() {
  SrcObjTableCleaner cleaner;
  _src_obj_table.iterate(&cleaner);
}
