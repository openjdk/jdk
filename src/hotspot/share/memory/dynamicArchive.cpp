/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "logging/log.hpp"
#include "memory/archiveUtils.inline.hpp"
#include "memory/dynamicArchive.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspace.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/resourceArea.hpp"
#include "oops/compressedOops.hpp"
#include "oops/objArrayKlass.hpp"
#include "prims/jvmtiRedefineClasses.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vmOperations.hpp"
#include "utilities/bitMap.inline.hpp"

#ifndef O_BINARY       // if defined (Win32) use binary files.
#define O_BINARY 0     // otherwise do nothing.
#endif

class DynamicArchiveBuilder : ResourceObj {
  static unsigned my_hash(const address& a) {
    return primitive_hash<address>(a);
  }
  static bool my_equals(const address& a0, const address& a1) {
    return primitive_equals<address>(a0, a1);
  }
  typedef ResourceHashtable<
      address, address,
      DynamicArchiveBuilder::my_hash,   // solaris compiler doesn't like: primitive_hash<address>
      DynamicArchiveBuilder::my_equals, // solaris compiler doesn't like: primitive_equals<address>
      16384, ResourceObj::C_HEAP> RelocationTable;
  RelocationTable _new_loc_table;

  static intx _buffer_to_target_delta;

  DumpRegion* _current_dump_space;

  static size_t reserve_alignment() {
    return os::vm_allocation_granularity();
  }

  static const int _total_dump_regions = 3;
  int _num_dump_regions_used;

public:
  void mark_pointer(address* ptr_loc) {
    ArchivePtrMarker::mark_pointer(ptr_loc);
  }

  DumpRegion* current_dump_space() const {
    return _current_dump_space;
  }

  bool is_in_buffer_space(address p) const {
    return (_alloc_bottom <= p && p < (address)current_dump_space()->top());
  }

  template <typename T> bool is_in_target_space(T target_obj) const {
    address buff_obj = address(target_obj) - _buffer_to_target_delta;
    return is_in_buffer_space(buff_obj);
  }

  template <typename T> bool is_in_buffer_space(T obj) const {
    return is_in_buffer_space(address(obj));
  }

  template <typename T> T to_target_no_check(T obj) const {
    return (T)(address(obj) + _buffer_to_target_delta);
  }

  template <typename T> T to_target(T obj) const {
    assert(is_in_buffer_space(obj), "must be");
    return (T)(address(obj) + _buffer_to_target_delta);
  }

  template <typename T> T get_new_loc(T obj) {
    address* pp = _new_loc_table.get((address)obj);
    if (pp == NULL) {
      // Excluded klasses are not copied
      return NULL;
    } else {
      return (T)*pp;
    }
  }

  address get_new_loc(MetaspaceClosure::Ref* ref) {
    return get_new_loc(ref->obj());
  }

  template <typename T> bool has_new_loc(T obj) {
    address* pp = _new_loc_table.get((address)obj);
    return pp != NULL;
  }

  static int dynamic_dump_method_comparator(Method* a, Method* b) {
    Symbol* a_name = a->name();
    Symbol* b_name = b->name();

    if (a_name == b_name) {
      return 0;
    }

    if (!MetaspaceShared::is_in_shared_metaspace(a_name)) {
      // a_name points to a Symbol in the top archive.
      // When this method is called, a_name is still pointing to the output space.
      // Translate it to point to the output space, so that it can be compared with
      // Symbols in the base archive.
      a_name = (Symbol*)(address(a_name) + _buffer_to_target_delta);
    }
    if (!MetaspaceShared::is_in_shared_metaspace(b_name)) {
      b_name = (Symbol*)(address(b_name) + _buffer_to_target_delta);
    }

    return a_name->fast_compare(b_name);
  }

protected:
  enum FollowMode {
    make_a_copy, point_to_it, set_to_null
  };

public:
  void copy(MetaspaceClosure::Ref* ref, bool read_only) {
    int bytes = ref->size() * BytesPerWord;
    address old_obj = ref->obj();
    address new_obj = copy_impl(ref, read_only, bytes);

    assert(new_obj != NULL, "must be");
    assert(new_obj != old_obj, "must be");
    bool isnew = _new_loc_table.put(old_obj, new_obj);
    assert(isnew, "must be");
  }

  // Make a shallow copy of each eligible MetaspaceObj into the buffer.
  class ShallowCopier: public UniqueMetaspaceClosure {
    DynamicArchiveBuilder* _builder;
    bool _read_only;
  public:
    ShallowCopier(DynamicArchiveBuilder* shuffler, bool read_only)
      : _builder(shuffler), _read_only(read_only) {}

    virtual bool do_unique_ref(Ref* orig_obj, bool read_only) {
      // This method gets called on each *original* object
      // reachable from _builder->iterate_roots(). Each orig_obj is
      // called exactly once.
      FollowMode mode = _builder->follow_ref(orig_obj);

      if (mode == point_to_it) {
        if (read_only == _read_only) {
          log_debug(cds, dynamic)("ptr : " PTR_FORMAT " %s", p2i(orig_obj->obj()),
                                  MetaspaceObj::type_name(orig_obj->msotype()));
          address p = orig_obj->obj();
          bool isnew = _builder->_new_loc_table.put(p, p);
          assert(isnew, "must be");
        }
        return false;
      }

      if (mode == set_to_null) {
        log_debug(cds, dynamic)("nul : " PTR_FORMAT " %s", p2i(orig_obj->obj()),
                                MetaspaceObj::type_name(orig_obj->msotype()));
        return false;
      }

      if (read_only == _read_only) {
        // Make a shallow copy of orig_obj in a buffer (maintained
        // by copy_impl in a subclass of DynamicArchiveBuilder).
        _builder->copy(orig_obj, read_only);
      }
      return true;
    }
  };

  // Relocate all embedded pointer fields within a MetaspaceObj's shallow copy
  class ShallowCopyEmbeddedRefRelocator: public UniqueMetaspaceClosure {
    DynamicArchiveBuilder* _builder;
  public:
    ShallowCopyEmbeddedRefRelocator(DynamicArchiveBuilder* shuffler)
      : _builder(shuffler) {}

    // This method gets called on each *original* object reachable
    // from _builder->iterate_roots(). Each orig_obj is
    // called exactly once.
    virtual bool do_unique_ref(Ref* orig_ref, bool read_only) {
      FollowMode mode = _builder->follow_ref(orig_ref);

      if (mode == point_to_it) {
        // We did not make a copy of this object
        // and we have nothing to update
        assert(_builder->get_new_loc(orig_ref) == NULL ||
               _builder->get_new_loc(orig_ref) == orig_ref->obj(), "must be");
        return false;
      }

      if (mode == set_to_null) {
        // We did not make a copy of this object
        // and we have nothing to update
        assert(!_builder->has_new_loc(orig_ref->obj()), "must not be copied or pointed to");
        return false;
      }

      // - orig_obj points to the original object.
      // - new_obj points to the shallow copy (created by ShallowCopier)
      //   of orig_obj. new_obj is NULL if the orig_obj is excluded
      address orig_obj = orig_ref->obj();
      address new_obj  = _builder->get_new_loc(orig_ref);

      assert(new_obj != orig_obj, "must be");
#ifdef ASSERT
      if (new_obj == NULL) {
        if (orig_ref->msotype() == MetaspaceObj::ClassType) {
          Klass* k = (Klass*)orig_obj;
          assert(k->is_instance_klass() &&
                 SystemDictionaryShared::is_excluded_class(InstanceKlass::cast(k)),
                 "orig_obj must be excluded Class");
        }
      }
#endif

      log_debug(cds, dynamic)("Relocating " PTR_FORMAT " %s", p2i(new_obj),
                              MetaspaceObj::type_name(orig_ref->msotype()));
      if (new_obj != NULL) {
        EmbeddedRefUpdater updater(_builder, orig_obj, new_obj);
        orig_ref->metaspace_pointers_do(&updater);
      }

      return true; // keep recursing until every object is visited exactly once.
    }

    virtual void push_special(SpecialRef type, Ref* ref, intptr_t* p) {
      assert(type == _method_entry_ref, "only special type allowed for now");
      address obj = ref->obj();
      address new_obj = _builder->get_new_loc(ref);
      size_t offset = pointer_delta(p, obj,  sizeof(u1));
      intptr_t* new_p = (intptr_t*)(new_obj + offset);
      assert(*p == *new_p, "must be a copy");
      ArchivePtrMarker::mark_pointer((address*)new_p);
    }
  };

  class EmbeddedRefUpdater: public MetaspaceClosure {
    DynamicArchiveBuilder* _builder;
    address _orig_obj;
    address _new_obj;
  public:
    EmbeddedRefUpdater(DynamicArchiveBuilder* shuffler, address orig_obj, address new_obj) :
      _builder(shuffler), _orig_obj(orig_obj), _new_obj(new_obj) {}

    // This method gets called once for each pointer field F of orig_obj.
    // We update new_obj->F to point to the new location of orig_obj->F.
    //
    // Example: Klass*  0x100 is copied to 0x400
    //          Symbol* 0x200 is copied to 0x500
    //
    // Let orig_obj == 0x100; and
    //     new_obj  == 0x400; and
    //     ((Klass*)orig_obj)->_name == 0x200;
    // Then this function effectively assigns
    //     ((Klass*)new_obj)->_name = 0x500;
    virtual bool do_ref(Ref* ref, bool read_only) {
      address new_pointee = NULL;

      if (ref->not_null()) {
        address old_pointee = ref->obj();

        FollowMode mode = _builder->follow_ref(ref);
        if (mode == point_to_it) {
          new_pointee = old_pointee;
        } else if (mode == set_to_null) {
          new_pointee = NULL;
        } else {
          new_pointee = _builder->get_new_loc(old_pointee);
        }
      }

      const char* kind = MetaspaceObj::type_name(ref->msotype());
      // offset of this field inside the original object
      intx offset = (address)ref->addr() - _orig_obj;
      _builder->update_pointer((address*)(_new_obj + offset), new_pointee, kind, offset);

      // We can't mark the pointer here, because DynamicArchiveBuilder::sort_methods
      // may re-layout the [iv]tables, which would change the offset(s) in an InstanceKlass
      // that would contain pointers. Therefore, we must mark the pointers after
      // sort_methods(), using PointerMarker.
      return false; // Do not recurse.
    }
  };

  class ExternalRefUpdater: public MetaspaceClosure {
    DynamicArchiveBuilder* _builder;

  public:
    ExternalRefUpdater(DynamicArchiveBuilder* shuffler) : _builder(shuffler) {}

    virtual bool do_ref(Ref* ref, bool read_only) {
      // ref is a pointer that lives OUTSIDE of the buffer, but points to an object inside the buffer
      if (ref->not_null()) {
        address new_loc = _builder->get_new_loc(ref);
        const char* kind = MetaspaceObj::type_name(ref->msotype());
        _builder->update_pointer(ref->addr(), new_loc, kind, 0);
        _builder->mark_pointer(ref->addr());
      }
      return false; // Do not recurse.
    }
  };

  class PointerMarker: public UniqueMetaspaceClosure {
    DynamicArchiveBuilder* _builder;

  public:
    PointerMarker(DynamicArchiveBuilder* shuffler) : _builder(shuffler) {}

    virtual bool do_unique_ref(Ref* ref, bool read_only) {
      if (_builder->is_in_buffer_space(ref->obj())) {
        EmbeddedRefMarker ref_marker(_builder);
        ref->metaspace_pointers_do(&ref_marker);
        return true; // keep recursing until every buffered object is visited exactly once.
      } else {
        return false;
      }
    }
  };

  class EmbeddedRefMarker: public MetaspaceClosure {
    DynamicArchiveBuilder* _builder;

  public:
    EmbeddedRefMarker(DynamicArchiveBuilder* shuffler) : _builder(shuffler) {}
    virtual bool do_ref(Ref* ref, bool read_only) {
      if (ref->not_null()) {
        _builder->mark_pointer(ref->addr());
      }
      return false; // Do not recurse.
    }
  };

  void update_pointer(address* addr, address value, const char* kind, uintx offset, bool is_mso_pointer=true) {
    // Propagate the the mask bits to the new value -- see comments above MetaspaceClosure::obj()
    if (is_mso_pointer) {
      const uintx FLAG_MASK = 0x03;
      uintx mask_bits = uintx(*addr) & FLAG_MASK;
      value = (address)(uintx(value) | mask_bits);
    }

    if (*addr != value) {
      log_debug(cds, dynamic)("Update (%18s*) %3d [" PTR_FORMAT "] " PTR_FORMAT " -> " PTR_FORMAT,
                              kind, int(offset), p2i(addr), p2i(*addr), p2i(value));
      *addr = value;
    }
  }

private:
  GrowableArray<Symbol*>* _symbols; // symbols to dump
  GrowableArray<InstanceKlass*>* _klasses; // klasses to dump

  void append(InstanceKlass* k) { _klasses->append(k); }
  void append(Symbol* s)        { _symbols->append(s); }

  class GatherKlassesAndSymbols : public UniqueMetaspaceClosure {
    DynamicArchiveBuilder* _builder;
    bool _read_only;

  public:
    GatherKlassesAndSymbols(DynamicArchiveBuilder* builder)
      : _builder(builder) {}

    virtual bool do_unique_ref(Ref* ref, bool read_only) {
      if (_builder->follow_ref(ref) != make_a_copy) {
        return false;
      }
      if (ref->msotype() == MetaspaceObj::ClassType) {
        Klass* klass = (Klass*)ref->obj();
        assert(klass->is_klass(), "must be");
        if (klass->is_instance_klass()) {
          InstanceKlass* ik = InstanceKlass::cast(klass);
          assert(!SystemDictionaryShared::is_excluded_class(ik), "must be");
          _builder->append(ik);
          _builder->_estimated_metsapceobj_bytes += BytesPerWord; // See RunTimeSharedClassInfo::get_for()
        }
      } else if (ref->msotype() == MetaspaceObj::SymbolType) {
        _builder->append((Symbol*)ref->obj());
      }

      int bytes = ref->size() * BytesPerWord;
      _builder->_estimated_metsapceobj_bytes += bytes;

      return true;
    }
  };

  FollowMode follow_ref(MetaspaceClosure::Ref *ref) {
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
        if (klass->is_instance_klass()) {
          InstanceKlass* ik = InstanceKlass::cast(klass);
          if (SystemDictionaryShared::is_excluded_class(ik)) {
            ResourceMark rm;
            log_debug(cds, dynamic)("Skipping class (excluded): %s", klass->external_name());
            return set_to_null;
          }
        } else if (klass->is_array_klass()) {
          // Don't support archiving of array klasses for now.
          ResourceMark rm;
          log_debug(cds, dynamic)("Skipping class (array): %s", klass->external_name());
          return set_to_null;
        }
      }

      return make_a_copy;
    }
  }

  address copy_impl(MetaspaceClosure::Ref* ref, bool read_only, int bytes) {
    if (ref->msotype() == MetaspaceObj::ClassType) {
      // Save a pointer immediate in front of an InstanceKlass, so
      // we can do a quick lookup from InstanceKlass* -> RunTimeSharedClassInfo*
      // without building another hashtable. See RunTimeSharedClassInfo::get_for()
      // in systemDictionaryShared.cpp.
      address obj = ref->obj();
      Klass* klass = (Klass*)obj;
      if (klass->is_instance_klass()) {
        SystemDictionaryShared::validate_before_archiving(InstanceKlass::cast(klass));
        current_dump_space()->allocate(sizeof(address), BytesPerWord);
      }
    }
    address p = (address)current_dump_space()->allocate(bytes);
    address obj = ref->obj();
    log_debug(cds, dynamic)("COPY: " PTR_FORMAT " ==> " PTR_FORMAT " %5d %s",
                            p2i(obj), p2i(p), bytes,
                            MetaspaceObj::type_name(ref->msotype()));
    memcpy(p, obj, bytes);
    intptr_t* cloned_vtable = MetaspaceShared::fix_cpp_vtable_for_dynamic_archive(ref->msotype(), p);
    if (cloned_vtable != NULL) {
      update_pointer((address*)p, (address)cloned_vtable, "vtb", 0, /*is_mso_pointer*/false);
      mark_pointer((address*)p);
    }

    return (address)p;
  }

  DynamicArchiveHeader *_header;
  address _alloc_bottom;
  address _last_verified_top;
  size_t _other_region_used_bytes;

  // Conservative estimate for number of bytes needed for:
  size_t _estimated_metsapceobj_bytes;   // all archived MetsapceObj's.
  size_t _estimated_hashtable_bytes;     // symbol table and dictionaries
  size_t _estimated_trampoline_bytes;    // method entry trampolines

  size_t estimate_archive_size();
  size_t estimate_trampoline_size();
  size_t estimate_class_file_size();
  address reserve_space_and_init_buffer_to_target_delta();
  void init_header(address addr);
  void release_header();
  void make_trampolines();
  void make_klasses_shareable();
  void sort_methods(InstanceKlass* ik) const;
  void set_symbols_permanent();
  void relocate_buffer_to_target();
  void write_archive(char* serialized_data);

  void init_first_dump_space(address reserved_bottom) {
    DumpRegion* mc_space = MetaspaceShared::misc_code_dump_space();
    DumpRegion* rw_space = MetaspaceShared::read_write_dump_space();

    // Use the same MC->RW->RO ordering as in the base archive.
    MetaspaceShared::init_shared_dump_space(mc_space);
    _current_dump_space = mc_space;
    _last_verified_top = reserved_bottom;
    _num_dump_regions_used = 1;
  }

  void reserve_buffers_for_trampolines() {
    size_t n = _estimated_trampoline_bytes;
    assert(n >= SharedRuntime::trampoline_size(), "dont want to be empty");
    MetaspaceShared::misc_code_space_alloc(n);
  }

public:
  DynamicArchiveBuilder() {
    _klasses = new (ResourceObj::C_HEAP, mtClass) GrowableArray<InstanceKlass*>(100, true, mtInternal);
    _symbols = new (ResourceObj::C_HEAP, mtClass) GrowableArray<Symbol*>(1000, true, mtInternal);

    _estimated_metsapceobj_bytes = 0;
    _estimated_hashtable_bytes = 0;
    _estimated_trampoline_bytes = 0;

    _num_dump_regions_used = 0;
  }

  void start_dump_space(DumpRegion* next) {
    address bottom = _last_verified_top;
    address top = (address)(current_dump_space()->top());
    _other_region_used_bytes += size_t(top - bottom);

    MetaspaceShared::pack_dump_space(current_dump_space(), next, MetaspaceShared::shared_rs());
    _current_dump_space = next;
    _num_dump_regions_used ++;

    _last_verified_top = (address)(current_dump_space()->top());
  }

  void verify_estimate_size(size_t estimate, const char* which) {
    address bottom = _last_verified_top;
    address top = (address)(current_dump_space()->top());
    size_t used = size_t(top - bottom) + _other_region_used_bytes;
    int diff = int(estimate) - int(used);

    log_info(cds)("%s estimate = " SIZE_FORMAT " used = " SIZE_FORMAT "; diff = %d bytes", which, estimate, used, diff);
    assert(diff >= 0, "Estimate is too small");

    _last_verified_top = top;
    _other_region_used_bytes = 0;
  }

  // Do this before and after the archive dump to see if any corruption
  // is caused by dynamic dumping.
  void verify_universe(const char* info) {
    if (VerifyBeforeExit) {
      log_info(cds)("Verify %s", info);
      HandleMark hm;
      // Among other things, this ensures that Eden top is correct.
      Universe::heap()->prepare_for_verify();
      Universe::verify(info);
    }
  }

  void doit() {
    verify_universe("Before CDS dynamic dump");
    DEBUG_ONLY(SystemDictionaryShared::NoClassLoadingMark nclm);
    SystemDictionaryShared::check_excluded_classes();

    {
      ResourceMark rm;
      GatherKlassesAndSymbols gatherer(this);

      SystemDictionaryShared::dumptime_classes_do(&gatherer);
      SymbolTable::metaspace_pointers_do(&gatherer);
      FileMapInfo::metaspace_pointers_do(&gatherer);

      gatherer.finish();
    }

    // rw space starts ...
    address reserved_bottom = reserve_space_and_init_buffer_to_target_delta();
    init_header(reserved_bottom);

    CHeapBitMap ptrmap;
    ArchivePtrMarker::initialize(&ptrmap, (address*)reserved_bottom, (address*)current_dump_space()->top());

    reserve_buffers_for_trampolines();
    verify_estimate_size(_estimated_trampoline_bytes, "Trampolines");

    start_dump_space(MetaspaceShared::read_write_dump_space());

    log_info(cds, dynamic)("Copying %d klasses and %d symbols",
                           _klasses->length(), _symbols->length());

    {
      assert(current_dump_space() == MetaspaceShared::read_write_dump_space(),
             "Current dump space is not rw space");
      // shallow-copy RW objects, if necessary
      ResourceMark rm;
      ShallowCopier rw_copier(this, false);
      iterate_roots(&rw_copier);
    }

    // ro space starts ...
    DumpRegion* ro_space = MetaspaceShared::read_only_dump_space();
    {
      start_dump_space(ro_space);

      // shallow-copy RO objects, if necessary
      ResourceMark rm;
      ShallowCopier ro_copier(this, true);
      iterate_roots(&ro_copier);
    }

    {
      log_info(cds)("Relocating embedded pointers ... ");
      ResourceMark rm;
      ShallowCopyEmbeddedRefRelocator emb_reloc(this);
      iterate_roots(&emb_reloc);
    }

    {
      log_info(cds)("Relocating external roots ... ");
      ResourceMark rm;
      ExternalRefUpdater ext_reloc(this);
      iterate_roots(&ext_reloc);
    }

    verify_estimate_size(_estimated_metsapceobj_bytes, "MetaspaceObjs");

    char* serialized_data;
    {
      set_symbols_permanent();

      // Write the symbol table and system dictionaries to the RO space.
      // Note that these tables still point to the *original* objects
      // (because they were not processed by ExternalRefUpdater), so
      // they would need to call DynamicArchive::original_to_target() to
      // get the correct addresses.
      assert(current_dump_space() == ro_space, "Must be RO space");
      SymbolTable::write_to_archive(false);
      SystemDictionaryShared::write_to_archive(false);

      serialized_data = ro_space->top();
      WriteClosure wc(ro_space);
      SymbolTable::serialize_shared_table_header(&wc, false);
      SystemDictionaryShared::serialize_dictionary_headers(&wc, false);
    }

    verify_estimate_size(_estimated_hashtable_bytes, "Hashtables");

    make_trampolines();
    make_klasses_shareable();

    {
      log_info(cds)("Adjust lambda proxy class dictionary");
      SystemDictionaryShared::adjust_lambda_proxy_class_dictionary();
    }

    {
      log_info(cds)("Final relocation of pointers ... ");
      ResourceMark rm;
      PointerMarker marker(this);
      iterate_roots(&marker);
      relocate_buffer_to_target();
    }

    write_archive(serialized_data);
    release_header();

    assert(_num_dump_regions_used == _total_dump_regions, "must be");
    verify_universe("After CDS dynamic dump");
  }

  void iterate_roots(MetaspaceClosure* it) {
    int i;
    int num_klasses = _klasses->length();
    for (i = 0; i < num_klasses; i++) {
      it->push(&_klasses->at(i));
    }

    int num_symbols = _symbols->length();
    for (i = 0; i < num_symbols; i++) {
      it->push(&_symbols->at(i));
    }

    FileMapInfo::metaspace_pointers_do(it);

    // Do not call these again, as we have already collected all the classes and symbols
    // that we want to archive. Also, these calls would corrupt the tables when
    // ExternalRefUpdater is used.
    //
    // SystemDictionaryShared::dumptime_classes_do(it);
    // SymbolTable::metaspace_pointers_do(it);

    it->finish();
  }
};

intx DynamicArchiveBuilder::_buffer_to_target_delta;


size_t DynamicArchiveBuilder::estimate_archive_size() {
  // size of the symbol table and two dictionaries, plus the RunTimeSharedClassInfo's
  _estimated_hashtable_bytes = 0;
  _estimated_hashtable_bytes += SymbolTable::estimate_size_for_archive();
  _estimated_hashtable_bytes += SystemDictionaryShared::estimate_size_for_archive();

  _estimated_trampoline_bytes = estimate_trampoline_size();

  size_t total = 0;

  total += _estimated_metsapceobj_bytes;
  total += _estimated_hashtable_bytes;
  total += _estimated_trampoline_bytes;

  // allow fragmentation at the end of each dump region
  total += _total_dump_regions * reserve_alignment();

  return align_up(total, reserve_alignment());
}

address DynamicArchiveBuilder::reserve_space_and_init_buffer_to_target_delta() {
  size_t total = estimate_archive_size();
  ReservedSpace rs(total);
  if (!rs.is_reserved()) {
    log_error(cds, dynamic)("Failed to reserve %d bytes of output buffer.", (int)total);
    vm_direct_exit(0);
  }

  address buffer_base = (address)rs.base();
  log_info(cds, dynamic)("Reserved output buffer space at    : " PTR_FORMAT " [%d bytes]",
                         p2i(buffer_base), (int)total);
  MetaspaceShared::set_shared_rs(rs);

  // At run time, we will mmap the dynamic archive at target_space_bottom.
  // However, at dump time, we may not be able to write into the target_space,
  // as it's occupied by dynamically loaded Klasses. So we allocate a buffer
  // at an arbitrary location chosen by the OS. We will write all the dynamically
  // archived classes into this buffer. At the final stage of dumping, we relocate
  // all pointers that are inside the buffer_space to point to their (runtime)
  // target location inside thetarget_space.
  address target_space_bottom =
    (address)align_up(MetaspaceShared::shared_metaspace_top(), reserve_alignment());
  _buffer_to_target_delta = intx(target_space_bottom) - intx(buffer_base);

  log_info(cds, dynamic)("Target archive space at            : " PTR_FORMAT, p2i(target_space_bottom));
  log_info(cds, dynamic)("Buffer-space to target-space delta : " PTR_FORMAT, p2i((address)_buffer_to_target_delta));

  return buffer_base;
}

void DynamicArchiveBuilder::init_header(address reserved_bottom) {
  _alloc_bottom = reserved_bottom;
  _last_verified_top = reserved_bottom;
  _other_region_used_bytes = 0;

  init_first_dump_space(reserved_bottom);

  FileMapInfo* mapinfo = new FileMapInfo(false);
  assert(FileMapInfo::dynamic_info() == mapinfo, "must be");
  _header = mapinfo->dynamic_header();

  Thread* THREAD = Thread::current();
  FileMapInfo* base_info = FileMapInfo::current_info();
  _header->set_base_header_crc(base_info->crc());
  for (int i = 0; i < MetaspaceShared::n_regions; i++) {
    _header->set_base_region_crc(i, base_info->space_crc(i));
  }
  _header->populate(base_info, os::vm_allocation_granularity());
}

void DynamicArchiveBuilder::release_header() {
  // We temporarily allocated a dynamic FileMapInfo for dumping, which makes it appear we
  // have mapped a dynamic archive, but we actually have not. We are in a safepoint now.
  // Let's free it so that if class loading happens after we leave the safepoint, nothing
  // bad will happen.
  assert(SafepointSynchronize::is_at_safepoint(), "must be");
  FileMapInfo *mapinfo = FileMapInfo::dynamic_info();
  assert(mapinfo != NULL && _header == mapinfo->dynamic_header(), "must be");
  delete mapinfo;
  assert(!DynamicArchive::is_mapped(), "must be");
  _header = NULL;
}

size_t DynamicArchiveBuilder::estimate_trampoline_size() {
  size_t total = 0;
  size_t each_method_bytes =
    align_up(SharedRuntime::trampoline_size(), BytesPerWord) +
    align_up(sizeof(AdapterHandlerEntry*), BytesPerWord);

  for (int i = 0; i < _klasses->length(); i++) {
    InstanceKlass* ik = _klasses->at(i);
    Array<Method*>* methods = ik->methods();
    total += each_method_bytes * methods->length();
  }
  if (total == 0) {
    // We have nothing to archive, but let's avoid having an empty region.
    total = SharedRuntime::trampoline_size();
  }
  return total;
}

void DynamicArchiveBuilder::make_trampolines() {
  DumpRegion* mc_space = MetaspaceShared::misc_code_dump_space();
  char* p = mc_space->base();
  for (int i = 0; i < _klasses->length(); i++) {
    InstanceKlass* ik = _klasses->at(i);
    Array<Method*>* methods = ik->methods();
    for (int j = 0; j < methods->length(); j++) {
      Method* m = methods->at(j);
      address c2i_entry_trampoline = (address)p;
      p += SharedRuntime::trampoline_size();
      assert(p >= mc_space->base() && p <= mc_space->top(), "must be");
      m->set_from_compiled_entry(to_target(c2i_entry_trampoline));

      AdapterHandlerEntry** adapter_trampoline =(AdapterHandlerEntry**)p;
      p += sizeof(AdapterHandlerEntry*);
      assert(p >= mc_space->base() && p <= mc_space->top(), "must be");
      *adapter_trampoline = NULL;
      m->set_adapter_trampoline(to_target(adapter_trampoline));
    }
  }

  guarantee(p <= mc_space->top(), "Estimate of trampoline size is insufficient");
}

void DynamicArchiveBuilder::make_klasses_shareable() {
  int i, count = _klasses->length();

  InstanceKlass::disable_method_binary_search();
  for (i = 0; i < count; i++) {
    InstanceKlass* ik = _klasses->at(i);
    sort_methods(ik);
  }

  for (i = 0; i < count; i++) {
    InstanceKlass* ik = _klasses->at(i);
    ik->assign_class_loader_type();

    MetaspaceShared::rewrite_nofast_bytecodes_and_calculate_fingerprints(Thread::current(), ik);
    ik->remove_unshareable_info();

    assert(ik->array_klasses() == NULL, "sanity");

    if (log_is_enabled(Debug, cds, dynamic)) {
      ResourceMark rm;
      log_debug(cds, dynamic)("klasses[%4i] = " PTR_FORMAT " %s", i, p2i(to_target(ik)), ik->external_name());
    }
  }
}

// The address order of the copied Symbols may be different than when the original
// klasses were created. Re-sort all the tables. See Method::sort_methods().
void DynamicArchiveBuilder::sort_methods(InstanceKlass* ik) const {
  assert(ik != NULL, "DynamicArchiveBuilder currently doesn't support dumping the base archive");
  if (MetaspaceShared::is_in_shared_metaspace(ik)) {
    // We have reached a supertype that's already in the base archive
    return;
  }

  if (ik->java_mirror() == NULL) {
    // NULL mirror means this class has already been visited and methods are already sorted
    return;
  }
  ik->remove_java_mirror();

  if (log_is_enabled(Debug, cds, dynamic)) {
    ResourceMark rm;
    log_debug(cds, dynamic)("sorting methods for " PTR_FORMAT " %s", p2i(to_target(ik)), ik->external_name());
  }

  // Make sure all supertypes have been sorted
  sort_methods(ik->java_super());
  Array<InstanceKlass*>* interfaces = ik->local_interfaces();
  int len = interfaces->length();
  for (int i = 0; i < len; i++) {
    sort_methods(interfaces->at(i));
  }

#ifdef ASSERT
  if (ik->methods() != NULL) {
    for (int m = 0; m < ik->methods()->length(); m++) {
      Symbol* name = ik->methods()->at(m)->name();
      assert(MetaspaceShared::is_in_shared_metaspace(name) || is_in_buffer_space(name), "must be");
    }
  }
  if (ik->default_methods() != NULL) {
    for (int m = 0; m < ik->default_methods()->length(); m++) {
      Symbol* name = ik->default_methods()->at(m)->name();
      assert(MetaspaceShared::is_in_shared_metaspace(name) || is_in_buffer_space(name), "must be");
    }
  }
#endif

  Thread* THREAD = Thread::current();
  Method::sort_methods(ik->methods(), /*set_idnums=*/true, dynamic_dump_method_comparator);
  if (ik->default_methods() != NULL) {
    Method::sort_methods(ik->default_methods(), /*set_idnums=*/false, dynamic_dump_method_comparator);
  }
  ik->vtable().initialize_vtable(true, THREAD); assert(!HAS_PENDING_EXCEPTION, "cannot fail");
  ik->itable().initialize_itable(true, THREAD); assert(!HAS_PENDING_EXCEPTION, "cannot fail");
}

void DynamicArchiveBuilder::set_symbols_permanent() {
  int count = _symbols->length();
  for (int i=0; i<count; i++) {
    Symbol* s = _symbols->at(i);
    s->set_permanent();

    if (log_is_enabled(Trace, cds, dynamic)) {
      ResourceMark rm;
      log_trace(cds, dynamic)("symbols[%4i] = " PTR_FORMAT " %s", i, p2i(to_target(s)), s->as_quoted_ascii());
    }
  }
}

class RelocateBufferToTarget: public BitMapClosure {
  DynamicArchiveBuilder *_builder;
  address* _buffer_bottom;
  intx _buffer_to_target_delta;
 public:
  RelocateBufferToTarget(DynamicArchiveBuilder* builder, address* bottom, intx delta) :
    _builder(builder), _buffer_bottom(bottom), _buffer_to_target_delta(delta) {}

  bool do_bit(size_t offset) {
    address* p = _buffer_bottom + offset;
    assert(_builder->is_in_buffer_space(p), "pointer must live in buffer space");

    address old_ptr = *p;
    if (_builder->is_in_buffer_space(old_ptr)) {
      address new_ptr = old_ptr + _buffer_to_target_delta;
      log_trace(cds, dynamic)("Final patch: @%6d [" PTR_FORMAT " -> " PTR_FORMAT "] " PTR_FORMAT " => " PTR_FORMAT,
                              (int)offset, p2i(p), p2i(_builder->to_target(p)),
                              p2i(old_ptr), p2i(new_ptr));
      *p = new_ptr;
    }

    return true; // keep iterating
  }
};

void DynamicArchiveBuilder::relocate_buffer_to_target() {
  RelocateBufferToTarget patcher(this, (address*)_alloc_bottom, _buffer_to_target_delta);
  ArchivePtrMarker::ptrmap()->iterate(&patcher);

  Array<u8>* table = FileMapInfo::saved_shared_path_table().table();
  SharedPathTable runtime_table(to_target(table), FileMapInfo::shared_path_table().size());
  _header->set_shared_path_table(runtime_table);

  address relocatable_base = (address)SharedBaseAddress;
  address relocatable_end = (address)(current_dump_space()->top()) + _buffer_to_target_delta;

  intx addr_delta = MetaspaceShared::final_delta();
  if (addr_delta == 0) {
    ArchivePtrMarker::compact(relocatable_base, relocatable_end);
  } else {
    // The base archive is NOT mapped at MetaspaceShared::requested_base_address() (due to ASLR).
    // This means that the current content of the dynamic archive is based on a random
    // address. Let's relocate all the pointers, so that it can be mapped to
    // MetaspaceShared::requested_base_address() without runtime relocation.
    //
    // Note: both the base and dynamic archive are written with
    // FileMapHeader::_requested_base_address == MetaspaceShared::requested_base_address()

    // Patch all pointers that are marked by ptrmap within this region,
    // where we have just dumped all the metaspace data.
    address patch_base = (address)_alloc_bottom;
    address patch_end  = (address)current_dump_space()->top();

    // the current value of the pointers to be patched must be within this
    // range (i.e., must point to either the top archive (as currently mapped), or to the
    // (targeted address of) the top archive)
    address valid_old_base = relocatable_base;
    address valid_old_end  = relocatable_end;
    size_t base_plus_top_size = valid_old_end - valid_old_base;
    size_t top_size = patch_end - patch_base;
    size_t base_size = base_plus_top_size - top_size;
    assert(base_plus_top_size > base_size, "no overflow");
    assert(base_plus_top_size > top_size, "no overflow");

    // after patching, the pointers must point inside this range
    // (the requested location of the archive, as mapped at runtime).
    address valid_new_base = (address)MetaspaceShared::requested_base_address();
    address valid_new_end  = valid_new_base + base_plus_top_size;

    log_debug(cds)("Relocating archive from [" INTPTR_FORMAT " - " INTPTR_FORMAT "] to "
                   "[" INTPTR_FORMAT " - " INTPTR_FORMAT "], delta = " INTX_FORMAT " bytes",
                   p2i(patch_base + base_size), p2i(patch_end),
                   p2i(valid_new_base + base_size), p2i(valid_new_end), addr_delta);

    SharedDataRelocator<true> patcher((address*)patch_base, (address*)patch_end, valid_old_base, valid_old_end,
                                      valid_new_base, valid_new_end, addr_delta, ArchivePtrMarker::ptrmap());
    ArchivePtrMarker::ptrmap()->iterate(&patcher);
    ArchivePtrMarker::compact(patcher.max_non_null_offset());
  }
}

void DynamicArchiveBuilder::write_archive(char* serialized_data) {
  int num_klasses = _klasses->length();
  int num_symbols = _symbols->length();

  _header->set_serialized_data(to_target(serialized_data));

  FileMapInfo* dynamic_info = FileMapInfo::dynamic_info();
  assert(dynamic_info != NULL, "Sanity");

  // Now write the archived data including the file offsets.
  const char* archive_name = Arguments::GetSharedDynamicArchivePath();
  dynamic_info->open_for_write(archive_name);
  MetaspaceShared::write_core_archive_regions(dynamic_info, NULL, NULL);
  dynamic_info->set_final_requested_base((char*)MetaspaceShared::requested_base_address());
  dynamic_info->set_header_crc(dynamic_info->compute_header_crc());
  dynamic_info->write_header();
  dynamic_info->close();

  address base = to_target(_alloc_bottom);
  address top  = address(current_dump_space()->top()) + _buffer_to_target_delta;
  size_t file_size = pointer_delta(top, base, sizeof(char));

  base += MetaspaceShared::final_delta();
  top += MetaspaceShared::final_delta();
  log_info(cds, dynamic)("Written dynamic archive " PTR_FORMAT " - " PTR_FORMAT
                         " [" SIZE_FORMAT " bytes header, " SIZE_FORMAT " bytes total]",
                         p2i(base), p2i(top), _header->header_size(), file_size);
  log_info(cds, dynamic)("%d klasses; %d symbols", num_klasses, num_symbols);
}


class VM_PopulateDynamicDumpSharedSpace: public VM_Operation {
  DynamicArchiveBuilder* _builder;
public:
  VM_PopulateDynamicDumpSharedSpace(DynamicArchiveBuilder* builder) : _builder(builder) {}
  VMOp_Type type() const { return VMOp_PopulateDumpSharedSpace; }
  void doit() {
    ResourceMark rm;
    if (SystemDictionaryShared::empty_dumptime_table()) {
      log_warning(cds, dynamic)("There is no class to be included in the dynamic archive.");
      return;
    }
    if (AllowArchivingWithJavaAgent) {
      warning("This archive was created with AllowArchivingWithJavaAgent. It should be used "
              "for testing purposes only and should not be used in a production environment");
    }
    FileMapInfo::check_nonempty_dir_in_shared_path_table();

    _builder->doit();
  }
};


void DynamicArchive::dump() {
  if (Arguments::GetSharedDynamicArchivePath() == NULL) {
    log_warning(cds, dynamic)("SharedDynamicArchivePath is not specified");
    return;
  }

  DynamicArchiveBuilder builder;
  _builder = &builder;
  VM_PopulateDynamicDumpSharedSpace op(&builder);
  VMThread::execute(&op);
  _builder = NULL;
}

address DynamicArchive::original_to_buffer_impl(address orig_obj) {
  assert(DynamicDumpSharedSpaces, "must be");
  address buff_obj = _builder->get_new_loc(orig_obj);
  assert(buff_obj != NULL, "orig_obj must be used by the dynamic archive");
  assert(buff_obj != orig_obj, "call this only when you know orig_obj must be copied and not just referenced");
  assert(_builder->is_in_buffer_space(buff_obj), "must be");
  return buff_obj;
}

address DynamicArchive::buffer_to_target_impl(address buff_obj) {
  assert(DynamicDumpSharedSpaces, "must be");
  assert(_builder->is_in_buffer_space(buff_obj), "must be");
  return _builder->to_target(buff_obj);
}

address DynamicArchive::original_to_target_impl(address orig_obj) {
  assert(DynamicDumpSharedSpaces, "must be");
  if (MetaspaceShared::is_in_shared_metaspace(orig_obj)) {
    // This happens when the top archive points to a Symbol* in the base archive.
    return orig_obj;
  }
  address buff_obj = _builder->get_new_loc(orig_obj);
  assert(buff_obj != NULL, "orig_obj must be used by the dynamic archive");
  if (buff_obj == orig_obj) {
    // We are storing a pointer to an original object into the dynamic buffer. E.g.,
    // a Symbol* that used by both the base and top archives.
    assert(MetaspaceShared::is_in_shared_metaspace(orig_obj), "must be");
    return orig_obj;
  } else {
    return _builder->to_target(buff_obj);
  }
}

uintx DynamicArchive::object_delta_uintx(void* buff_obj) {
  assert(DynamicDumpSharedSpaces, "must be");
  address target_obj = _builder->to_target_no_check(address(buff_obj));
  assert(uintx(target_obj) >= SharedBaseAddress, "must be");
  return uintx(target_obj) - SharedBaseAddress;
}

bool DynamicArchive::is_in_target_space(void *obj) {
  assert(DynamicDumpSharedSpaces, "must be");
  return _builder->is_in_target_space(obj);
}


DynamicArchiveBuilder* DynamicArchive::_builder = NULL;


bool DynamicArchive::validate(FileMapInfo* dynamic_info) {
  // Check if the recorded base archive matches with the current one
  FileMapInfo* base_info = FileMapInfo::current_info();
  DynamicArchiveHeader* dynamic_header = dynamic_info->dynamic_header();

  // Check the header crc
  if (dynamic_header->base_header_crc() != base_info->crc()) {
    FileMapInfo::fail_continue("Archive header checksum verification failed.");
    return false;
  }

  // Check each space's crc
  for (int i = 0; i < MetaspaceShared::n_regions; i++) {
    if (dynamic_header->base_region_crc(i) != base_info->space_crc(i)) {
      FileMapInfo::fail_continue("Archive region #%d checksum verification failed.", i);
      return false;
    }
  }

  // Validate the dynamic archived shared path table, and set the global
  // _shared_path_table to that.
  if (!dynamic_info->validate_shared_path_table()) {
    return false;
  }
  return true;
}
