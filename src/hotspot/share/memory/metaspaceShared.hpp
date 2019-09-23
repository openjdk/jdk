/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACESHARED_HPP
#define SHARE_MEMORY_METASPACESHARED_HPP

#include "classfile/compactHashtable.hpp"
#include "memory/allocation.hpp"
#include "memory/memRegion.hpp"
#include "memory/virtualspace.hpp"
#include "oops/oop.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/macros.hpp"
#include "utilities/resourceHash.hpp"

#define MAX_SHARED_DELTA                (0x7FFFFFFF)

class FileMapInfo;

class MetaspaceSharedStats {
public:
  MetaspaceSharedStats() {
    memset(this, 0, sizeof(*this));
  }
  CompactHashtableStats symbol;
  CompactHashtableStats string;
};

#if INCLUDE_CDS
class DumpRegion {
private:
  const char* _name;
  char* _base;
  char* _top;
  char* _end;
  bool _is_packed;

public:
  DumpRegion(const char* name) : _name(name), _base(NULL), _top(NULL), _end(NULL), _is_packed(false) {}

  char* expand_top_to(char* newtop);
  char* allocate(size_t num_bytes, size_t alignment=BytesPerWord);

  void append_intptr_t(intptr_t n) {
    assert(is_aligned(_top, sizeof(intptr_t)), "bad alignment");
    intptr_t *p = (intptr_t*)_top;
    char* newtop = _top + sizeof(intptr_t);
    expand_top_to(newtop);
    *p = n;
  }

  char* base()      const { return _base;        }
  char* top()       const { return _top;         }
  char* end()       const { return _end;         }
  size_t reserved() const { return _end - _base; }
  size_t used()     const { return _top - _base; }
  bool is_packed()  const { return _is_packed;   }
  bool is_allocatable() const {
    return !is_packed() && _base != NULL;
  }

  void print(size_t total_bytes) const;
  void print_out_of_space_msg(const char* failing_region, size_t needed_bytes);

  void init(const ReservedSpace* rs, char* base) {
    if (base == NULL) {
      base = rs->base();
    }
    assert(rs->contains(base), "must be");
    _base = _top = base;
    _end = rs->end();
  }
  void init(char* b, char* t, char* e) {
    _base = b;
    _top = t;
    _end = e;
  }

  void pack(DumpRegion* next = NULL);

  bool contains(char* p) {
    return base() <= p && p < top();
  }
};

// Closure for serializing initialization data out to a data area to be
// written to the shared file.

class WriteClosure : public SerializeClosure {
private:
  DumpRegion* _dump_region;

public:
  WriteClosure(DumpRegion* r) {
    _dump_region = r;
  }

  void do_ptr(void** p) {
    _dump_region->append_intptr_t((intptr_t)*p);
  }

  void do_u4(u4* p) {
    void* ptr = (void*)(uintx(*p));
    do_ptr(&ptr);
  }

  void do_bool(bool *p) {
    void* ptr = (void*)(uintx(*p));
    do_ptr(&ptr);
  }

  void do_tag(int tag) {
    _dump_region->append_intptr_t((intptr_t)tag);
  }

  void do_oop(oop* o);

  void do_region(u_char* start, size_t size);

  bool reading() const { return false; }
};

// Closure for serializing initialization data in from a data area
// (ptr_array) read from the shared file.

class ReadClosure : public SerializeClosure {
private:
  intptr_t** _ptr_array;

  inline intptr_t nextPtr() {
    return *(*_ptr_array)++;
  }

public:
  ReadClosure(intptr_t** ptr_array) { _ptr_array = ptr_array; }

  void do_ptr(void** p);

  void do_u4(u4* p);

  void do_bool(bool *p);

  void do_tag(int tag);

  void do_oop(oop *p);

  void do_region(u_char* start, size_t size);

  bool reading() const { return true; }
};

#endif

// Class Data Sharing Support
class MetaspaceShared : AllStatic {

  // CDS support
  static ReservedSpace _shared_rs;
  static VirtualSpace _shared_vs;
  static int _max_alignment;
  static MetaspaceSharedStats _stats;
  static bool _has_error_classes;
  static bool _archive_loading_failed;
  static bool _remapped_readwrite;
  static address _i2i_entry_code_buffers;
  static size_t  _i2i_entry_code_buffers_size;
  static size_t  _core_spaces_size;
  static void* _shared_metaspace_static_top;
 public:
  enum {
    // core archive spaces
    mc = 0,  // miscellaneous code for method trampolines
    rw = 1,  // read-write shared space in the heap
    ro = 2,  // read-only shared space in the heap
    md = 3,  // miscellaneous data for initializing tables, etc.
    num_core_spaces = 4, // number of non-string regions
    num_non_heap_spaces = 4,

    // mapped java heap regions
    first_closed_archive_heap_region = md + 1,
    max_closed_archive_heap_region = 2,
    last_closed_archive_heap_region = first_closed_archive_heap_region + max_closed_archive_heap_region - 1,
    first_open_archive_heap_region = last_closed_archive_heap_region + 1,
    max_open_archive_heap_region = 2,
    last_open_archive_heap_region = first_open_archive_heap_region + max_open_archive_heap_region - 1,

    last_valid_region = last_open_archive_heap_region,
    n_regions =  last_valid_region + 1 // total number of regions
  };

  static void prepare_for_dumping() NOT_CDS_RETURN;
  static void preload_and_dump(TRAPS) NOT_CDS_RETURN;
  static int preload_classes(const char * class_list_path,
                             TRAPS) NOT_CDS_RETURN_(0);

  static GrowableArray<Klass*>* collected_klasses();

  static ReservedSpace* shared_rs() {
    CDS_ONLY(return &_shared_rs);
    NOT_CDS(return NULL);
  }
  static void commit_shared_space_to(char* newtop) NOT_CDS_RETURN;
  static size_t core_spaces_size() {
    assert(DumpSharedSpaces || UseSharedSpaces, "sanity");
    assert(_core_spaces_size != 0, "sanity");
    return _core_spaces_size;
  }
  static void initialize_dumptime_shared_and_meta_spaces() NOT_CDS_RETURN;
  static void initialize_runtime_shared_and_meta_spaces() NOT_CDS_RETURN;
  static char* initialize_dynamic_runtime_shared_spaces(
                     char* static_start, char* static_end) NOT_CDS_RETURN_(NULL);
  static void post_initialize(TRAPS) NOT_CDS_RETURN;

  // Delta of this object from SharedBaseAddress
  static uintx object_delta_uintx(void* obj);

  static u4 object_delta_u4(void* obj) {
    // offset is guaranteed to be less than MAX_SHARED_DELTA in DumpRegion::expand_top_to()
    uintx deltax = object_delta_uintx(obj);
    guarantee(deltax <= MAX_SHARED_DELTA, "must be 32-bit offset");
    return (u4)deltax;
  }

  static void set_archive_loading_failed() {
    _archive_loading_failed = true;
  }
  static bool map_shared_spaces(FileMapInfo* mapinfo) NOT_CDS_RETURN_(false);
  static void initialize_shared_spaces() NOT_CDS_RETURN;

  // Return true if given address is in the shared metaspace regions (i.e., excluding any
  // mapped shared heap regions.)
  static bool is_in_shared_metaspace(const void* p) {
    // If no shared metaspace regions are mapped, MetaspceObj::_shared_metaspace_{base,top} will
    // both be NULL and all values of p will be rejected quickly.
    return (p < MetaspaceObj::shared_metaspace_top() && p >= MetaspaceObj::shared_metaspace_base());
  }

  static address shared_metaspace_top() {
    return (address)MetaspaceObj::shared_metaspace_top();
  }

  static void set_shared_metaspace_range(void* base, void* top) NOT_CDS_RETURN;

  // Return true if given address is in the shared region corresponding to the idx
  static bool is_in_shared_region(const void* p, int idx) NOT_CDS_RETURN_(false);

  static bool is_in_trampoline_frame(address addr) NOT_CDS_RETURN_(false);

  static bool is_shared_dynamic(void* p) NOT_CDS_RETURN_(false);

  static void allocate_cpp_vtable_clones();
  static intptr_t* clone_cpp_vtables(intptr_t* p);
  static void zero_cpp_vtable_clones_for_writing();
  static void patch_cpp_vtable_pointers();
  static void serialize_cloned_cpp_vtptrs(SerializeClosure* sc);

  static bool is_valid_shared_method(const Method* m) NOT_CDS_RETURN_(false);
  static void serialize(SerializeClosure* sc) NOT_CDS_RETURN;

  static MetaspaceSharedStats* stats() {
    return &_stats;
  }

  static void report_out_of_space(const char* name, size_t needed_bytes);

  // JVM/TI RedefineClasses() support:
  // Remap the shared readonly space to shared readwrite, private if
  // sharing is enabled. Simply returns true if sharing is not enabled
  // or if the remapping has already been done by a prior call.
  static bool remap_shared_readonly_as_readwrite() NOT_CDS_RETURN_(true);
  static bool remapped_readwrite() {
    CDS_ONLY(return _remapped_readwrite);
    NOT_CDS(return false);
  }

  static bool try_link_class(InstanceKlass* ik, TRAPS);
  static void link_and_cleanup_shared_classes(TRAPS);

#if INCLUDE_CDS
  static ReservedSpace* reserve_shared_rs(size_t size, size_t alignment,
                                          bool large, char* requested_address);
  static void init_shared_dump_space(DumpRegion* first_space, address first_space_bottom = NULL);
  static DumpRegion* misc_code_dump_space();
  static DumpRegion* read_write_dump_space();
  static DumpRegion* read_only_dump_space();
  static void pack_dump_space(DumpRegion* current, DumpRegion* next,
                              ReservedSpace* rs);

  static void rewrite_nofast_bytecodes_and_calculate_fingerprints(InstanceKlass* ik);
#endif

  // Allocate a block of memory from the "mc", "ro", or "rw" regions.
  static char* misc_code_space_alloc(size_t num_bytes);
  static char* read_only_space_alloc(size_t num_bytes);

  template <typename T>
  static Array<T>* new_ro_array(int length) {
#if INCLUDE_CDS
    size_t byte_size = Array<T>::byte_sizeof(length, sizeof(T));
    Array<T>* array = (Array<T>*)read_only_space_alloc(byte_size);
    array->initialize(length);
    return array;
#else
    return NULL;
#endif
  }

  template <typename T>
  static size_t ro_array_bytesize(int length) {
    size_t byte_size = Array<T>::byte_sizeof(length, sizeof(T));
    return align_up(byte_size, BytesPerWord);
  }

  static address i2i_entry_code_buffers(size_t total_size);

  static address i2i_entry_code_buffers() {
    return _i2i_entry_code_buffers;
  }
  static size_t i2i_entry_code_buffers_size() {
    return _i2i_entry_code_buffers_size;
  }
  static void relocate_klass_ptr(oop o);

  static Klass* get_relocated_klass(Klass *k);

  static intptr_t* fix_cpp_vtable_for_dynamic_archive(MetaspaceObj::Type msotype, address obj);

private:
  static void read_extra_data(const char* filename, TRAPS) NOT_CDS_RETURN;
};
#endif // SHARE_MEMORY_METASPACESHARED_HPP
