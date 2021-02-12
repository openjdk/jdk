/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_ARCHIVEBUILDER_HPP
#define SHARE_MEMORY_ARCHIVEBUILDER_HPP

#include "memory/archiveUtils.hpp"
#include "memory/metaspaceClosure.hpp"
#include "oops/klass.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/hashtable.hpp"
#include "utilities/resourceHash.hpp"

class CHeapBitMap;
class DumpAllocStats;
class FileMapInfo;
class Klass;
class MemRegion;
class Symbol;

class ArchiveBuilder : public StackObj {
public:
  enum FollowMode {
    make_a_copy, point_to_it, set_to_null
  };

private:
  class SpecialRefInfo {
    // We have a "special pointer" of the given _type at _field_offset of _src_obj.
    // See MetaspaceClosure::push_special().
    MetaspaceClosure::SpecialRef _type;
    address _src_obj;
    size_t _field_offset;

  public:
    SpecialRefInfo() {}
    SpecialRefInfo(MetaspaceClosure::SpecialRef type, address src_obj, size_t field_offset)
      : _type(type), _src_obj(src_obj), _field_offset(field_offset) {}

    MetaspaceClosure::SpecialRef type() const { return _type;         }
    address src_obj()                   const { return _src_obj;      }
    size_t field_offset()               const { return _field_offset; }
  };

  class SourceObjInfo {
    MetaspaceClosure::Ref* _ref;
    uintx _ptrmap_start;     // The bit-offset of the start of this object (inclusive)
    uintx _ptrmap_end;       // The bit-offset of the end   of this object (exclusive)
    bool _read_only;
    FollowMode _follow_mode;
    int _size_in_bytes;
    MetaspaceObj::Type _msotype;
    address _dumped_addr;    // Address this->obj(), as used by the dumped archive.
    address _orig_obj;       // The value of the original object (_ref->obj()) when this
                             // SourceObjInfo was created. Note that _ref->obj() may change
                             // later if _ref is relocated.

  public:
    SourceObjInfo(MetaspaceClosure::Ref* ref, bool read_only, FollowMode follow_mode) :
      _ref(ref), _ptrmap_start(0), _ptrmap_end(0), _read_only(read_only), _follow_mode(follow_mode),
      _size_in_bytes(ref->size() * BytesPerWord), _msotype(ref->msotype()),
      _orig_obj(ref->obj()) {
      if (follow_mode == point_to_it) {
        _dumped_addr = ref->obj();
      } else {
        _dumped_addr = NULL;
      }
    }

    bool should_copy() const { return _follow_mode == make_a_copy; }
    MetaspaceClosure::Ref* ref() const { return  _ref; }
    void set_dumped_addr(address dumped_addr)  {
      assert(should_copy(), "must be");
      assert(_dumped_addr == NULL, "cannot be copied twice");
      assert(dumped_addr != NULL, "must be a valid copy");
      _dumped_addr = dumped_addr;
    }
    void set_ptrmap_start(uintx v) { _ptrmap_start = v;    }
    void set_ptrmap_end(uintx v)   { _ptrmap_end = v;      }
    uintx ptrmap_start()  const    { return _ptrmap_start; } // inclusive
    uintx ptrmap_end()    const    { return _ptrmap_end;   } // exclusive
    bool read_only()      const    { return _read_only;    }
    int size_in_bytes()   const    { return _size_in_bytes; }
    address orig_obj()    const    { return _orig_obj; }
    address dumped_addr() const    { return _dumped_addr; }
    MetaspaceObj::Type msotype() const { return _msotype; }

    // convenience accessor
    address obj() const { return ref()->obj(); }
  };

  class SourceObjList {
    uintx _total_bytes;
    GrowableArray<SourceObjInfo*>* _objs;     // Source objects to be archived
    CHeapBitMap _ptrmap;                      // Marks the addresses of the pointer fields
                                              // in the source objects
  public:
    SourceObjList();
    ~SourceObjList();

    GrowableArray<SourceObjInfo*>* objs() const { return _objs; }

    void append(MetaspaceClosure::Ref* enclosing_ref, SourceObjInfo* src_info);
    void remember_embedded_pointer(SourceObjInfo* pointing_obj, MetaspaceClosure::Ref* ref);
    void relocate(int i, ArchiveBuilder* builder);

    // convenience accessor
    SourceObjInfo* at(int i) const { return objs()->at(i); }
  };

  class SrcObjTableCleaner {
  public:
    bool do_entry(address key, const SourceObjInfo* value) {
      delete value->ref();
      return true;
    }
  };

  class CDSMapLogger;

  static const int INITIAL_TABLE_SIZE = 15889;
  static const int MAX_TABLE_SIZE     = 1000000;

  DumpRegion* _mc_region;
  DumpRegion* _rw_region;
  DumpRegion* _ro_region;

  SourceObjList _rw_src_objs;                 // objs to put in rw region
  SourceObjList _ro_src_objs;                 // objs to put in ro region
  KVHashtable<address, SourceObjInfo, mtClassShared> _src_obj_table;
  GrowableArray<Klass*>* _klasses;
  GrowableArray<Symbol*>* _symbols;
  GrowableArray<SpecialRefInfo>* _special_refs;

  // statistics
  int _num_instance_klasses;
  int _num_obj_array_klasses;
  int _num_type_array_klasses;
  DumpAllocStats* _alloc_stats;

  // For global access.
  static ArchiveBuilder* _singleton;

public:
  // Use this when you allocate space with MetaspaceShare::read_only_space_alloc()
  // outside of ArchiveBuilder::dump_{rw,ro}_region. These are usually for misc tables
  // that are allocated in the RO space.
  class OtherROAllocMark {
    char* _oldtop;
  public:
    OtherROAllocMark() {
      _oldtop = _singleton->_ro_region->top();
    }
    ~OtherROAllocMark();
  };

private:
  FollowMode get_follow_mode(MetaspaceClosure::Ref *ref);

  void iterate_sorted_roots(MetaspaceClosure* it, bool is_relocating_pointers);
  void sort_symbols_and_fix_hash();
  void sort_klasses();
  static int compare_symbols_by_address(Symbol** a, Symbol** b);
  static int compare_klass_by_name(Klass** a, Klass** b);

  void make_shallow_copies(DumpRegion *dump_region, const SourceObjList* src_objs);
  void make_shallow_copy(DumpRegion *dump_region, SourceObjInfo* src_info);

  void update_special_refs();
  void relocate_embedded_pointers(SourceObjList* src_objs);
  void relocate_roots();

  bool is_excluded(Klass* k);
  void clean_up_src_obj_table();
protected:
  virtual void iterate_roots(MetaspaceClosure* it, bool is_relocating_pointers) = 0;

  // Conservative estimate for number of bytes needed for:
  size_t _estimated_metaspaceobj_bytes;   // all archived MetaspaceObj's.

protected:
  DumpRegion* _current_dump_space;
  address _alloc_bottom;

  DumpRegion* current_dump_space() const {  return _current_dump_space;  }

public:
  void set_current_dump_space(DumpRegion* r) { _current_dump_space = r; }

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

public:
  ArchiveBuilder(DumpRegion* mc_region, DumpRegion* rw_region, DumpRegion* ro_region);
  ~ArchiveBuilder();

  void gather_klasses_and_symbols();
  void gather_source_objs();
  bool gather_klass_and_symbol(MetaspaceClosure::Ref* ref, bool read_only);
  bool gather_one_source_obj(MetaspaceClosure::Ref* enclosing_ref, MetaspaceClosure::Ref* ref, bool read_only);
  void add_special_ref(MetaspaceClosure::SpecialRef type, address src_obj, size_t field_offset);
  void remember_embedded_pointer_in_copied_obj(MetaspaceClosure::Ref* enclosing_ref, MetaspaceClosure::Ref* ref);

  void dump_rw_region();
  void dump_ro_region();
  void relocate_pointers();
  void relocate_vm_classes();
  void make_klasses_shareable();
  void write_cds_map_to_log(FileMapInfo* mapinfo,
                            GrowableArray<MemRegion> *closed_heap_regions,
                            GrowableArray<MemRegion> *open_heap_regions,
                            char* bitmap, size_t bitmap_size_in_bytes);

  address get_dumped_addr(address src_obj) const;

  // All klasses and symbols that will be copied into the archive
  GrowableArray<Klass*>*  klasses() const { return _klasses; }
  GrowableArray<Symbol*>* symbols() const { return _symbols; }

  static ArchiveBuilder* singleton() {
    assert(_singleton != NULL, "ArchiveBuilder must be active");
    return _singleton;
  }

  static DumpAllocStats* alloc_stats() {
    return singleton()->_alloc_stats;
  }

  static Klass* get_relocated_klass(Klass* orig_klass) {
    Klass* klass = (Klass*)singleton()->get_dumped_addr((address)orig_klass);
    assert(klass != NULL && klass->is_klass(), "must be");
    return klass;
  }

  static Symbol* get_relocated_symbol(Symbol* orig_symbol) {
    return (Symbol*)singleton()->get_dumped_addr((address)orig_symbol);
  }

  void print_stats(int ro_all, int rw_all, int mc_all);
  static intx _buffer_to_target_delta;

  // Method trampolines related functions
  void allocate_method_trampolines();
  void allocate_method_trampolines_for(InstanceKlass* ik);
  size_t allocate_method_trampoline_info();
  void update_method_trampolines();

};

#endif // SHARE_MEMORY_ARCHIVEBUILDER_HPP
