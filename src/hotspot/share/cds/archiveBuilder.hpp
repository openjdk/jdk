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

#ifndef SHARE_CDS_ARCHIVEBUILDER_HPP
#define SHARE_CDS_ARCHIVEBUILDER_HPP

#include "cds/archiveUtils.hpp"
#include "cds/dumpAllocStats.hpp"
#include "memory/metaspaceClosure.hpp"
#include "oops/array.hpp"
#include "oops/klass.hpp"
#include "runtime/os.hpp"
#include "utilities/bitMap.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resizeableResourceHash.hpp"
#include "utilities/resourceHash.hpp"

class ArchiveHeapInfo;
class CHeapBitMap;
class FileMapInfo;
class Klass;
class MemRegion;
class Symbol;

// Metaspace::allocate() requires that all blocks must be aligned with KlassAlignmentInBytes.
// We enforce the same alignment rule in blocks allocated from the shared space.
const int SharedSpaceObjectAlignment = KlassAlignmentInBytes;

// Overview of CDS archive creation (for both static and dynamic dump):
//
// [1] Load all classes (static dump: from the classlist, dynamic dump: as part of app execution)
// [2] Allocate "output buffer"
// [3] Copy contents of the 2 "core" regions (rw/ro) into the output buffer.
//       - allocate the cpp vtables in rw (static dump only)
//       - memcpy the MetaspaceObjs into rw/ro:
//         dump_rw_region();
//         dump_ro_region();
//       - fix all the pointers in the MetaspaceObjs to point to the copies
//         relocate_metaspaceobj_embedded_pointers()
// [4] Copy symbol table, dictionary, etc, into the ro region
// [5] Relocate all the pointers in rw/ro, so that the archive can be mapped to
//     the "requested" location without runtime relocation. See relocate_to_requested()
//
// "source" vs "buffered" vs "requested"
//
// The ArchiveBuilder deals with three types of addresses.
//
// "source":    These are the addresses of objects created in step [1] above. They are the actual
//              InstanceKlass*, Method*, etc, of the Java classes that are loaded for executing
//              Java bytecodes in the JVM process that's dumping the CDS archive.
//
//              It may be necessary to contiue Java execution after ArchiveBuilder is finished.
//              Therefore, we don't modify any of the "source" objects.
//
// "buffered":  The "source" objects that are deemed archivable are copied into a temporary buffer.
//              Objects in the buffer are modified in steps [2, 3, 4] (e.g., unshareable info is
//              removed, pointers are relocated, etc) to prepare them to be loaded at runtime.
//
// "requested": These are the addreses where the "buffered" objects should be loaded at runtime.
//              When the "buffered" objects are written into the archive file, their addresses
//              are adjusted in step [5] such that the lowest of these objects would be mapped
//              at SharedBaseAddress.
//
// Translation between "source" and "buffered" addresses is done with two hashtables:
//     _src_obj_table          : "source"   -> "buffered"
//     _buffered_to_src_table  : "buffered" -> "source"
//
// Translation between "buffered" and "requested" addresses is done with a simple shift:
//    buffered_address + _buffer_to_requested_delta == requested_address
//
class ArchiveBuilder : public StackObj {
protected:
  DumpRegion* _current_dump_region;
  address _buffer_bottom;                      // for writing the contents of rw/ro regions
  address _last_verified_top;
  int _num_dump_regions_used;
  size_t _other_region_used_bytes;

  // These are the addresses where we will request the static and dynamic archives to be
  // mapped at run time. If the request fails (due to ASLR), we will map the archives at
  // os-selected addresses.
  address _requested_static_archive_bottom;     // This is determined solely by the value of
                                                // SharedBaseAddress during -Xshare:dump.
  address _requested_static_archive_top;
  address _requested_dynamic_archive_bottom;    // Used only during dynamic dump. It's placed
                                                // immediately above _requested_static_archive_top.
  address _requested_dynamic_archive_top;

  // (Used only during dynamic dump) where the static archive is actually mapped. This
  // may be different than _requested_static_archive_{bottom,top} due to ASLR
  address _mapped_static_archive_bottom;
  address _mapped_static_archive_top;

  intx _buffer_to_requested_delta;

  DumpRegion* current_dump_region() const {  return _current_dump_region;  }

public:
  enum FollowMode {
    make_a_copy, point_to_it, set_to_null
  };

private:
  class SourceObjInfo {
    uintx _ptrmap_start;     // The bit-offset of the start of this object (inclusive)
    uintx _ptrmap_end;       // The bit-offset of the end   of this object (exclusive)
    bool _read_only;
    bool _has_embedded_pointer;
    FollowMode _follow_mode;
    int _size_in_bytes;
    int _id; // Each object has a unique serial ID, starting from zero. The ID is assigned
             // when the object is added into _source_objs.
    MetaspaceObj::Type _msotype;
    address _source_addr;    // The source object to be copied.
    address _buffered_addr;  // The copy of this object insider the buffer.
  public:
    SourceObjInfo(MetaspaceClosure::Ref* ref, bool read_only, FollowMode follow_mode) :
      _ptrmap_start(0), _ptrmap_end(0), _read_only(read_only), _has_embedded_pointer(false), _follow_mode(follow_mode),
      _size_in_bytes(ref->size() * BytesPerWord), _id(0), _msotype(ref->msotype()),
      _source_addr(ref->obj()) {
      if (follow_mode == point_to_it) {
        _buffered_addr = ref->obj();
      } else {
        _buffered_addr = nullptr;
      }
    }

    // This constructor is only used for regenerated objects (created by LambdaFormInvokers, etc).
    //   src = address of a Method or InstanceKlass that has been regenerated.
    //   renegerated_obj_info = info for the regenerated version of src.
    SourceObjInfo(address src, SourceObjInfo* renegerated_obj_info) :
      _ptrmap_start(0), _ptrmap_end(0), _read_only(false),
      _follow_mode(renegerated_obj_info->_follow_mode),
      _size_in_bytes(0), _msotype(renegerated_obj_info->_msotype),
      _source_addr(src),  _buffered_addr(renegerated_obj_info->_buffered_addr) {}

    bool should_copy() const { return _follow_mode == make_a_copy; }
    void set_buffered_addr(address addr)  {
      assert(should_copy(), "must be");
      assert(_buffered_addr == nullptr, "cannot be copied twice");
      assert(addr != nullptr, "must be a valid copy");
      _buffered_addr = addr;
    }
    void set_ptrmap_start(uintx v) { _ptrmap_start = v;    }
    void set_ptrmap_end(uintx v)   { _ptrmap_end = v;      }
    uintx ptrmap_start()  const    { return _ptrmap_start; } // inclusive
    uintx ptrmap_end()    const    { return _ptrmap_end;   } // exclusive
    bool read_only()      const    { return _read_only;    }
    bool has_embedded_pointer() const { return _has_embedded_pointer; }
    void set_has_embedded_pointer()   { _has_embedded_pointer = true; }
    int size_in_bytes()   const    { return _size_in_bytes; }
    int id()              const    { return _id; }
    void set_id(int i)             { _id = i; }
    address source_addr() const    { return _source_addr; }
    address buffered_addr() const  {
      if (_follow_mode != set_to_null) {
        assert(_buffered_addr != nullptr, "must be initialized");
      }
      return _buffered_addr;
    }
    MetaspaceObj::Type msotype() const { return _msotype; }
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

    void append(SourceObjInfo* src_info);
    void remember_embedded_pointer(SourceObjInfo* pointing_obj, MetaspaceClosure::Ref* ref);
    void relocate(int i, ArchiveBuilder* builder);

    // convenience accessor
    SourceObjInfo* at(int i) const { return objs()->at(i); }
  };

  class CDSMapLogger;

  static const int INITIAL_TABLE_SIZE = 15889;
  static const int MAX_TABLE_SIZE     = 1000000;

  ReservedSpace _shared_rs;
  VirtualSpace _shared_vs;

  DumpRegion _rw_region;
  DumpRegion _ro_region;

  // Combined bitmap to track pointers in both RW and RO regions. This is updated
  // as objects are copied into RW and RO.
  CHeapBitMap _ptrmap;

  // _ptrmap is split into these two bitmaps which are written into the archive.
  CHeapBitMap _rw_ptrmap;   // marks pointers in the RW region
  CHeapBitMap _ro_ptrmap;   // marks pointers in the RO region

  SourceObjList _rw_src_objs;                 // objs to put in rw region
  SourceObjList _ro_src_objs;                 // objs to put in ro region
  ResizeableResourceHashtable<address, SourceObjInfo, AnyObj::C_HEAP, mtClassShared> _src_obj_table;
  ResizeableResourceHashtable<address, address, AnyObj::C_HEAP, mtClassShared> _buffered_to_src_table;
  GrowableArray<Klass*>* _klasses;
  GrowableArray<Symbol*>* _symbols;
  unsigned int _entropy_seed;

  // statistics
  DumpAllocStats _alloc_stats;
  size_t _total_heap_region_size;

  void print_region_stats(FileMapInfo *map_info, ArchiveHeapInfo* heap_info);
  void print_bitmap_region_stats(size_t size, size_t total_size);
  void print_heap_region_stats(ArchiveHeapInfo* heap_info, size_t total_size);

  // For global access.
  static ArchiveBuilder* _current;

public:
  // Use this when you allocate space outside of ArchiveBuilder::dump_{rw,ro}_region.
  // These are usually for misc tables that are allocated in the RO space.
  class OtherROAllocMark {
    char* _oldtop;
  public:
    OtherROAllocMark() {
      _oldtop = _current->_ro_region.top();
    }
    ~OtherROAllocMark();
  };

private:
  FollowMode get_follow_mode(MetaspaceClosure::Ref *ref);

  void iterate_sorted_roots(MetaspaceClosure* it);
  void sort_klasses();
  static int compare_symbols_by_address(Symbol** a, Symbol** b);
  static int compare_klass_by_name(Klass** a, Klass** b);

  void make_shallow_copies(DumpRegion *dump_region, const SourceObjList* src_objs);
  void make_shallow_copy(DumpRegion *dump_region, SourceObjInfo* src_info);

  void relocate_embedded_pointers(SourceObjList* src_objs);

  bool is_excluded(Klass* k);
  void clean_up_src_obj_table();

protected:
  virtual void iterate_roots(MetaspaceClosure* it) = 0;

  // Conservative estimate for number of bytes needed for:
  size_t _estimated_metaspaceobj_bytes;   // all archived MetaspaceObj's.
  size_t _estimated_hashtable_bytes;     // symbol table and dictionaries

  static const int _total_dump_regions = 2;

  size_t estimate_archive_size();

  void start_dump_region(DumpRegion* next);
  void verify_estimate_size(size_t estimate, const char* which);

public:
  address reserve_buffer();

  address buffer_bottom()                    const { return _buffer_bottom;                        }
  address buffer_top()                       const { return (address)current_dump_region()->top(); }
  address requested_static_archive_bottom()  const { return  _requested_static_archive_bottom;     }
  address mapped_static_archive_bottom()     const { return  _mapped_static_archive_bottom;        }
  intx buffer_to_requested_delta()           const { return _buffer_to_requested_delta;            }

  bool is_in_buffer_space(address p) const {
    return (buffer_bottom() <= p && p < buffer_top());
  }

  template <typename T> bool is_in_requested_static_archive(T p) const {
    return _requested_static_archive_bottom <= (address)p && (address)p < _requested_static_archive_top;
  }

  template <typename T> bool is_in_mapped_static_archive(T p) const {
    return _mapped_static_archive_bottom <= (address)p && (address)p < _mapped_static_archive_top;
  }

  template <typename T> bool is_in_buffer_space(T obj) const {
    return is_in_buffer_space(address(obj));
  }

  template <typename T> T to_requested(T obj) const {
    assert(is_in_buffer_space(obj), "must be");
    return (T)(address(obj) + _buffer_to_requested_delta);
  }

  static intx get_buffer_to_requested_delta() {
    return current()->buffer_to_requested_delta();
  }

  inline static u4 to_offset_u4(uintx offset) {
    guarantee(offset <= MAX_SHARED_DELTA, "must be 32-bit offset " INTPTR_FORMAT, offset);
    return (u4)offset;
  }

public:
  static const uintx MAX_SHARED_DELTA = 0x7FFFFFFF;

  // The address p points to an object inside the output buffer. When the archive is mapped
  // at the requested address, what's the offset of this object from _requested_static_archive_bottom?
  uintx buffer_to_offset(address p) const;

  // Same as buffer_to_offset, except that the address p points to either (a) an object
  // inside the output buffer, or (b), an object in the currently mapped static archive.
  uintx any_to_offset(address p) const;

  template <typename T>
  u4 buffer_to_offset_u4(T p) const {
    uintx offset = buffer_to_offset((address)p);
    return to_offset_u4(offset);
  }

  template <typename T>
  u4 any_to_offset_u4(T p) const {
    uintx offset = any_to_offset((address)p);
    return to_offset_u4(offset);
  }

  static void assert_is_vm_thread() PRODUCT_RETURN;

public:
  ArchiveBuilder();
  ~ArchiveBuilder();

  int entropy();
  void gather_klasses_and_symbols();
  void gather_source_objs();
  bool gather_klass_and_symbol(MetaspaceClosure::Ref* ref, bool read_only);
  bool gather_one_source_obj(MetaspaceClosure::Ref* ref, bool read_only);
  void remember_embedded_pointer_in_enclosing_obj(MetaspaceClosure::Ref* ref);
  static void serialize_dynamic_archivable_items(SerializeClosure* soc);

  DumpRegion* rw_region() { return &_rw_region; }
  DumpRegion* ro_region() { return &_ro_region; }

  static char* rw_region_alloc(size_t num_bytes) {
    return current()->rw_region()->allocate(num_bytes);
  }
  static char* ro_region_alloc(size_t num_bytes) {
    return current()->ro_region()->allocate(num_bytes);
  }

  template <typename T>
  static Array<T>* new_ro_array(int length) {
    size_t byte_size = Array<T>::byte_sizeof(length, sizeof(T));
    Array<T>* array = (Array<T>*)ro_region_alloc(byte_size);
    array->initialize(length);
    return array;
  }

  template <typename T>
  static Array<T>* new_rw_array(int length) {
    size_t byte_size = Array<T>::byte_sizeof(length, sizeof(T));
    Array<T>* array = (Array<T>*)rw_region_alloc(byte_size);
    array->initialize(length);
    return array;
  }

  template <typename T>
  static size_t ro_array_bytesize(int length) {
    size_t byte_size = Array<T>::byte_sizeof(length, sizeof(T));
    return align_up(byte_size, SharedSpaceObjectAlignment);
  }

  char* ro_strdup(const char* s);

  static int compare_src_objs(SourceObjInfo** a, SourceObjInfo** b);
  void sort_metadata_objs();
  void dump_rw_metadata();
  void dump_ro_metadata();
  void relocate_metaspaceobj_embedded_pointers();
  void record_regenerated_object(address orig_src_obj, address regen_src_obj);
  void make_klasses_shareable();
  void relocate_to_requested();
  void write_archive(FileMapInfo* mapinfo, ArchiveHeapInfo* heap_info);
  void write_region(FileMapInfo* mapinfo, int region_idx, DumpRegion* dump_region,
                    bool read_only,  bool allow_exec);

  void write_pointer_in_buffer(address* ptr_location, address src_addr);
  template <typename T> void write_pointer_in_buffer(T* ptr_location, T src_addr) {
    write_pointer_in_buffer((address*)ptr_location, (address)src_addr);
  }

  void mark_and_relocate_to_buffered_addr(address* ptr_location);
  template <typename T> void mark_and_relocate_to_buffered_addr(T ptr_location) {
    mark_and_relocate_to_buffered_addr((address*)ptr_location);
  }

  address get_buffered_addr(address src_addr) const;
  template <typename T> T get_buffered_addr(T src_addr) const {
    return (T)get_buffered_addr((address)src_addr);
  }

  address get_source_addr(address buffered_addr) const;
  template <typename T> T get_source_addr(T buffered_addr) const {
    return (T)get_source_addr((address)buffered_addr);
  }

  // All klasses and symbols that will be copied into the archive
  GrowableArray<Klass*>*  klasses() const { return _klasses; }
  GrowableArray<Symbol*>* symbols() const { return _symbols; }

  static bool is_active() {
    return (_current != nullptr);
  }

  static ArchiveBuilder* current() {
    assert_is_vm_thread();
    assert(_current != nullptr, "ArchiveBuilder must be active");
    return _current;
  }

  static DumpAllocStats* alloc_stats() {
    return &(current()->_alloc_stats);
  }

  static CompactHashtableStats* symbol_stats() {
    return alloc_stats()->symbol_stats();
  }

  static CompactHashtableStats* string_stats() {
    return alloc_stats()->string_stats();
  }

  narrowKlass get_requested_narrow_klass(Klass* k);

  static Klass* get_buffered_klass(Klass* src_klass) {
    Klass* klass = (Klass*)current()->get_buffered_addr((address)src_klass);
    assert(klass != nullptr && klass->is_klass(), "must be");
    return klass;
  }

  static Symbol* get_buffered_symbol(Symbol* src_symbol) {
    return (Symbol*)current()->get_buffered_addr((address)src_symbol);
  }

  void print_stats();
  void report_out_of_space(const char* name, size_t needed_bytes);
};

#endif // SHARE_CDS_ARCHIVEBUILDER_HPP
