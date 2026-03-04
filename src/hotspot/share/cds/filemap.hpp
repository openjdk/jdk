/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_FILEMAP_HPP
#define SHARE_CDS_FILEMAP_HPP

#include "cds/aotCompressedPointers.hpp"
#include "cds/aotMappedHeap.hpp"
#include "cds/aotMetaspace.hpp"
#include "cds/aotStreamedHeap.hpp"
#include "cds/archiveUtils.hpp"
#include "cds/heapShared.hpp"
#include "include/cds.h"
#include "logging/logLevel.hpp"
#include "memory/allocation.hpp"
#include "oops/array.hpp"
#include "oops/compressedOops.hpp"
#include "utilities/align.hpp"
#include "utilities/bitMap.hpp"

// To understand the layout of the CDS archive file:
//
// java -Xlog:cds+map=info:file=cds.map:none:filesize=0
// java -Xlog:cds+map=debug:file=cds.map:none:filesize=0
// java -Xlog:cds+map=trace:file=cds.map:none:filesize=0

static const int JVM_IDENT_MAX = 256;

class AOTClassLocationConfig;
class BitMapView;
class CHeapBitMap;
class ClassFileStream;
class ClassLoaderData;
class ClassPathEntry;
class outputStream;
class ReservedSpace;

class FileMapRegion: private CDSFileMapRegion {
public:
  void assert_is_heap_region() const {
    assert(_is_heap_region, "must be heap region");
  }
  void assert_is_not_heap_region() const {
    assert(!_is_heap_region, "must not be heap region");
  }

  static FileMapRegion* cast(CDSFileMapRegion* p) {
    return (FileMapRegion*)p;
  }

  // Accessors
  int crc()                         const { return _crc; }
  size_t file_offset()              const { return _file_offset; }
  size_t mapping_offset()           const { return _mapping_offset; }
  size_t mapping_end_offset()       const { return _mapping_offset + used_aligned(); }
  size_t used()                     const { return _used; }
  size_t used_aligned()             const; // aligned up to AOTMetaspace::core_region_alignment()
  char*  mapped_base()              const { return _mapped_base; }
  char*  mapped_end()               const { return mapped_base()        + used_aligned(); }
  bool   read_only()                const { return _read_only != 0; }
  bool   allow_exec()               const { return _allow_exec != 0; }
  bool   mapped_from_file()         const { return _mapped_from_file != 0; }
  size_t oopmap_offset()            const { assert_is_heap_region();     return _oopmap_offset; }
  size_t oopmap_size_in_bits()      const { assert_is_heap_region();     return _oopmap_size_in_bits; }
  size_t ptrmap_offset()            const { return _ptrmap_offset; }
  size_t ptrmap_size_in_bits()      const { return _ptrmap_size_in_bits; }
  bool   in_reserved_space()        const { return _in_reserved_space; }

  void set_file_offset(size_t s)     { _file_offset = s; }
  void set_read_only(bool v)         { _read_only = v; }
  void set_mapped_base(char* p)      { _mapped_base = p; }
  void set_mapped_from_file(bool v)  { _mapped_from_file = v; }
  void set_in_reserved_space(bool is_reserved) { _in_reserved_space = is_reserved; }
  void init(int region_index, size_t mapping_offset, size_t size, bool read_only,
            bool allow_exec, int crc);
  void init_oopmap(size_t offset, size_t size_in_bits);
  void init_ptrmap(size_t offset, size_t size_in_bits);
  bool has_ptrmap()                  { return _ptrmap_size_in_bits != 0; }

  bool check_region_crc(char* base) const;
  void print(outputStream* st, int region_index);
};

class FileMapHeader: private CDSFileMapHeaderBase {
  friend class CDSConstants;
  friend class VMStructs;
  using narrowPtr = AOTCompressedPointers::narrowPtr;
private:
  // The following fields record the states of the VM during dump time.
  // They are compared with the runtime states to see if the archive
  // can be used.
  size_t _core_region_alignment;                  // how shared archive should be aligned
  int    _obj_alignment;                          // value of ObjectAlignmentInBytes
  address _narrow_oop_base;                       // compressed oop encoding base
  int    _narrow_oop_shift;                       // compressed oop encoding shift
  bool   _compact_strings;                        // value of CompactStrings
  bool   _compact_headers;                        // value of UseCompactObjectHeaders
  uintx  _max_heap_size;                          // java max heap size during dumping
  CompressedOops::Mode _narrow_oop_mode;          // compressed oop encoding mode
  bool    _object_streaming_mode;                 // dump was created for object streaming
  bool    _compressed_oops;                       // save the flag UseCompressedOops
  bool    _compressed_class_ptrs;                 // save the flag UseCompressedClassPointers
  int     _narrow_klass_pointer_bits;             // save number of bits in narrowKlass
  int     _narrow_klass_shift;                    // save shift width used to pre-compute narrowKlass IDs in archived heap objects
  narrowPtr _cloned_vtables;                      // The address of the first cloned vtable
  narrowPtr _early_serialized_data;               // Data accessed using {ReadClosure,WriteClosure}::serialize()
  narrowPtr _serialized_data;                     // Data accessed using {ReadClosure,WriteClosure}::serialize()

  // The following fields are all sanity checks for whether this archive
  // will function correctly with this JVM and the bootclasspath it's
  // invoked with.
  char  _jvm_ident[JVM_IDENT_MAX];  // identifier string of the jvm that created this dump

  narrowPtr _class_location_config;

  bool   _verify_local;                 // BytecodeVerificationLocal setting
  bool   _verify_remote;                // BytecodeVerificationRemote setting
  bool   _has_platform_or_app_classes;  // Archive contains app or platform classes
  char*  _requested_base_address;       // Archive relocation is not necessary if we map with this base address.
  char*  _mapped_base_address;          // Actual base address where archive is mapped.

  bool   _use_optimized_module_handling;// No module-relation VM options were specified, so we can skip
                                        // some expensive operations.
  bool   _has_aot_linked_classes;       // Was the CDS archive created with -XX:+AOTClassLinking
  bool   _has_full_module_graph;        // Does this CDS archive contain the full archived module graph?
  size_t _rw_ptrmap_start_pos;          // The first bit in the ptrmap corresponds to this position in the rw region
  size_t _ro_ptrmap_start_pos;          // The first bit in the ptrmap corresponds to this position in the ro region

  AOTMappedHeapHeader _mapped_heap_header;
  AOTStreamedHeapHeader _streamed_heap_header;

  // The following are parameters that affect MethodData layout.
  u1      _compiler_type;
  uint    _type_profile_level;
  int     _type_profile_args_limit;
  int     _type_profile_parms_limit;
  intx    _type_profile_width;
  intx    _bci_profile_width;
  bool    _profile_traps;
  bool    _type_profile_casts;
  int     _spec_trap_limit_extra_entries;

  template <typename T> T decode(narrowPtr narrowp) const {
    return AOTCompressedPointers::decode_not_null<T>(narrowp, reinterpret_cast<address>(mapped_base_address()));
  }

public:
  // Accessors -- fields declared in GenericCDSFileMapHeader
  unsigned int magic()                            const { return _generic_header._magic;                    }
  int crc()                                       const { return _generic_header._crc;                      }
  int version()                                   const { return _generic_header._version;                  }
  unsigned int header_size()                      const { return _generic_header._header_size;              }
  unsigned int base_archive_name_offset()         const { return _generic_header._base_archive_name_offset; }
  unsigned int base_archive_name_size()           const { return _generic_header._base_archive_name_size;   }

  void set_magic(unsigned int m)                            { _generic_header._magic = m;                    }
  void set_crc(int crc_value)                               { _generic_header._crc = crc_value;              }
  void set_version(int v)                                   { _generic_header._version = v;                  }
  void set_header_size(unsigned int s)                      { _generic_header._header_size = s;              }
  void set_base_archive_name_offset(unsigned int s)         { _generic_header._base_archive_name_offset = s; }
  void set_base_archive_name_size(unsigned int s)           { _generic_header._base_archive_name_size = s;   }

  bool is_static()                         const { return magic() == CDS_ARCHIVE_MAGIC; }
  size_t core_region_alignment()           const { return _core_region_alignment; }
  int obj_alignment()                      const { return _obj_alignment; }
  address narrow_oop_base()                const { return _narrow_oop_base; }
  int narrow_oop_shift()                   const { return _narrow_oop_shift; }
  bool compact_strings()                   const { return _compact_strings; }
  bool compact_headers()                   const { return _compact_headers; }
  uintx max_heap_size()                    const { return _max_heap_size; }
  CompressedOops::Mode narrow_oop_mode()   const { return _narrow_oop_mode; }
  char* cloned_vtables()                   const { return decode<char*>(_cloned_vtables); }
  char* early_serialized_data()            const { return decode<char*>(_early_serialized_data); }
  char* serialized_data()                  const { return decode<char*>(_serialized_data); }
  bool object_streaming_mode()             const { return _object_streaming_mode; }
  const char* jvm_ident()                  const { return _jvm_ident; }
  char* requested_base_address()           const { return _requested_base_address; }
  char* mapped_base_address()              const { return _mapped_base_address; }
  bool has_platform_or_app_classes()       const { return _has_platform_or_app_classes; }
  bool has_aot_linked_classes()            const { return _has_aot_linked_classes; }
  bool compressed_oops()                   const { return _compressed_oops; }
  bool compressed_class_pointers()         const { return _compressed_class_ptrs; }
  int narrow_klass_pointer_bits()          const { return _narrow_klass_pointer_bits; }
  int narrow_klass_shift()                 const { return _narrow_klass_shift; }
  bool has_full_module_graph()             const { return _has_full_module_graph; }
  size_t rw_ptrmap_start_pos()             const { return _rw_ptrmap_start_pos; }
  size_t ro_ptrmap_start_pos()             const { return _ro_ptrmap_start_pos; }

  // Heap archiving
  const AOTMappedHeapHeader*   mapped_heap()   const { return &_mapped_heap_header; }
  const AOTStreamedHeapHeader* streamed_heap() const { return &_streamed_heap_header; }

  void set_streamed_heap_header(AOTStreamedHeapHeader header) { _streamed_heap_header = header; }
  void set_mapped_heap_header(AOTMappedHeapHeader header) { _mapped_heap_header = header; }

  void set_has_platform_or_app_classes(bool v)   { _has_platform_or_app_classes = v; }
  void set_cloned_vtables(char* p)               { _cloned_vtables = AOTCompressedPointers::encode_not_null(p); }
  void set_early_serialized_data(char* p)        { _early_serialized_data = AOTCompressedPointers::encode_not_null(p); }
  void set_serialized_data(char* p)              { _serialized_data = AOTCompressedPointers::encode_not_null(p); }
  void set_mapped_base_address(char* p)          { _mapped_base_address = p; }
  void set_rw_ptrmap_start_pos(size_t n)         { _rw_ptrmap_start_pos = n; }
  void set_ro_ptrmap_start_pos(size_t n)         { _ro_ptrmap_start_pos = n; }

  void copy_base_archive_name(const char* name);

  void set_class_location_config(AOTClassLocationConfig* table) {
    _class_location_config = AOTCompressedPointers::encode_not_null(table);
  }

  AOTClassLocationConfig* class_location_config() {
    return decode<AOTClassLocationConfig*>(_class_location_config);
  }

  void set_requested_base(char* b) {
    _requested_base_address = b;
    _mapped_base_address = nullptr;
  }

  bool validate();
  int compute_crc();

  FileMapRegion* region_at(int i) {
    assert(is_valid_region(i), "invalid region");
    return FileMapRegion::cast(&_regions[i]);
  }

  void populate(FileMapInfo *info, size_t core_region_alignment, size_t header_size,
                size_t base_archive_name_size, size_t base_archive_name_offset);
  static bool is_valid_region(int region) {
    return (0 <= region && region < NUM_CDS_REGIONS);
  }

  void print(outputStream* st);
};

class FileMapInfo : public CHeapObj<mtInternal> {
private:
  friend class ManifestStream;
  friend class VMStructs;
  friend class ArchiveBuilder;
  friend class CDSOffsets;
  friend class FileMapHeader;

  bool           _is_static;
  bool           _file_open;
  bool           _is_mapped;
  int            _fd;
  size_t         _file_offset;
  const char*    _full_path;
  const char*    _base_archive_name;
  FileMapHeader* _header;

  // FileMapHeader describes the shared space data in the file to be
  // mapped.  This structure gets written to a file.  It is not a class, so
  // that the compilers don't add any compiler-private data to it.

  static FileMapInfo* _current_info;
  static FileMapInfo* _dynamic_archive_info;
  static bool _memory_mapping_failed;

public:
  FileMapHeader *header() const       { return _header; }
  static bool get_base_archive_name_from_header(const char* archive_name,
                                                const char** base_archive_name);
  static bool is_preimage_static_archive(const char* file);

  bool init_from_file(int fd);

  void log_paths(const char* msg, int start_idx, int end_idx);

  FileMapInfo(const char* full_path, bool is_static);
  ~FileMapInfo();
  static void free_current_info();

  // Accessors
  int    compute_header_crc()  const { return header()->compute_crc(); }
  void   set_header_crc(int crc)     { header()->set_crc(crc); }
  int    region_crc(int i)     const { return region_at(i)->crc(); }
  void   populate_header(size_t core_region_alignment);
  bool   validate_header();
  void   invalidate();
  int    crc()                 const { return header()->crc(); }
  int    version()             const { return header()->version(); }
  unsigned int magic()         const { return header()->magic(); }
  address narrow_oop_base()    const { return header()->narrow_oop_base(); }
  int     narrow_oop_shift()   const { return header()->narrow_oop_shift(); }
  uintx   max_heap_size()      const { return header()->max_heap_size(); }
  size_t  core_region_alignment() const { return header()->core_region_alignment(); }

  const AOTMappedHeapHeader*   mapped_heap()   const { return header()->mapped_heap(); }
  const AOTStreamedHeapHeader* streamed_heap() const { return header()->streamed_heap(); }

  bool object_streaming_mode()                const { return header()->object_streaming_mode(); }
  CompressedOops::Mode narrow_oop_mode()      const { return header()->narrow_oop_mode(); }

  char* cloned_vtables()                      const { return header()->cloned_vtables(); }
  void  set_cloned_vtables(char* p)           const { header()->set_cloned_vtables(p); }
  char* early_serialized_data()               const { return header()->early_serialized_data(); }
  void  set_early_serialized_data(char* p)    const { header()->set_early_serialized_data(p); }
  char* serialized_data()                     const { return header()->serialized_data(); }
  void  set_serialized_data(char* p)          const { header()->set_serialized_data(p); }

  bool  is_file_position_aligned() const;
  void  align_file_position();

  bool is_static()                            const { return _is_static; }
  bool is_mapped()                            const { return _is_mapped; }
  void set_is_mapped(bool v)                        { _is_mapped = v; }
  const char* full_path()                     const { return _full_path; }
  char* map_heap_region(FileMapRegion* r, char* addr, size_t bytes);

  void set_requested_base(char* b)                  { header()->set_requested_base(b); }
  char* requested_base_address()           const    { return header()->requested_base_address(); }

  class DynamicArchiveHeader* dynamic_header() const {
    assert(!is_static(), "must be");
    return (DynamicArchiveHeader*)header();
  }

  void set_has_platform_or_app_classes(bool v) {
    header()->set_has_platform_or_app_classes(v);
  }
  bool has_platform_or_app_classes() const {
    return header()->has_platform_or_app_classes();
  }

  static FileMapInfo* current_info() {
    CDS_ONLY(return _current_info;)
    NOT_CDS(return nullptr;)
  }

  static void set_current_info(FileMapInfo* info) {
    CDS_ONLY(_current_info = info;)
  }

  static FileMapInfo* dynamic_info() {
    CDS_ONLY(return _dynamic_archive_info;)
    NOT_CDS(return nullptr;)
  }

  static void assert_mark(bool check);

  // File manipulation.
  bool  open_as_input() NOT_CDS_RETURN_(false);
  void  open_as_output();
  void  prepare_for_writing();
  void  write_header();
  void  write_region(int region, char* base, size_t size,
                     bool read_only, bool allow_exec);
  size_t remove_bitmap_zeros(CHeapBitMap* map);
  char* write_bitmap_region(CHeapBitMap* rw_ptrmap,
                            CHeapBitMap* ro_ptrmap,
                            AOTMappedHeapInfo* mapped_heap_info,
                            AOTStreamedHeapInfo* streamed_heap_info,
                            size_t &size_in_bytes);
  size_t write_mapped_heap_region(AOTMappedHeapInfo* heap_info) NOT_CDS_JAVA_HEAP_RETURN_(0);
  size_t write_streamed_heap_region(AOTStreamedHeapInfo* heap_info) NOT_CDS_JAVA_HEAP_RETURN_(0);
  void  write_bytes(const void* buffer, size_t count);
  void  write_bytes_aligned(const void* buffer, size_t count);
  size_t  read_bytes(void* buffer, size_t count);
  static size_t readonly_total();
  MapArchiveResult map_regions(int regions[], int num_regions, char* mapped_base_address, ReservedSpace rs);
  void  unmap_regions(int regions[], int num_regions);

  // Object loading support
  void  stream_heap_region() NOT_CDS_JAVA_HEAP_RETURN;
  void  map_or_load_heap_region() NOT_CDS_JAVA_HEAP_RETURN;

  bool  has_heap_region()  NOT_CDS_JAVA_HEAP_RETURN_(false);
  bool  read_region(int i, char* base, size_t size, bool do_commit);
  char* map_bitmap_region();
  bool  map_aot_code_region(ReservedSpace rs);
  char* map_forwarding_region();
  void  unmap_region(int i);
  void  close();
  bool  is_open() { return _file_open; }

  // JVM/TI RedefineClasses() support:
  // Remap the shared readonly space to shared readwrite, private.
  bool  remap_shared_readonly_as_readwrite();

  static bool memory_mapping_failed() {
    CDS_ONLY(return _memory_mapping_failed;)
    NOT_CDS(return false;)
  }

  bool validate_class_location();
  bool validate_aot_class_linking();

#if INCLUDE_JVMTI
  // Caller needs a ResourceMark because parts of the returned cfs are resource-allocated.
  static ClassFileStream* open_stream_for_jvmti(InstanceKlass* ik, Handle class_loader, TRAPS);
  static ClassFileStream* get_stream_from_class_loader(Handle class_loader,
                                                       ClassPathEntry* cpe,
                                                       const char* file_name,
                                                       TRAPS);
#endif

  // The offset of the first core region in the archive, relative to SharedBaseAddress
  size_t mapping_base_offset() const { return first_core_region()->mapping_offset();    }
  // The offset of the (exclusive) end of the last core region in this archive, relative to SharedBaseAddress
  size_t mapping_end_offset()  const { return last_core_region()->mapping_end_offset(); }

  char* mapped_base()    const { return header()->mapped_base_address();    }
  char* mapped_end()     const { return last_core_region()->mapped_end();   }

  // Non-zero if the archive needs to be mapped a non-default location due to ASLR.
  intx relocation_delta() const {
    return header()->mapped_base_address() - header()->requested_base_address();
  }

  FileMapRegion* first_core_region() const;
  FileMapRegion* last_core_region()  const;

  FileMapRegion* region_at(int i) const {
    return header()->region_at(i);
  }

  BitMapView bitmap_view(int region_index, bool is_oopmap);
  BitMapView oopmap_view(int region_index);
  BitMapView ptrmap_view(int region_index);

  void print(outputStream* st) const;

  const char* vm_version() {
    return header()->jvm_ident();
  }
  bool  can_use_heap_region();

 private:
  bool  open_for_read();
  void  seek_to_position(size_t pos);

  MapArchiveResult map_region(int i, intx addr_delta, char* mapped_base_address, ReservedSpace rs);
  bool  relocate_pointers_in_core_regions(intx addr_delta);
  char* map_auxiliary_region(int region_index, bool read_only);

public:

private:

#if INCLUDE_JVMTI
  static ClassPathEntry** _classpath_entries_for_jvmti;
  static ClassPathEntry* get_classpath_entry_for_jvmti(int i, TRAPS);
#endif
};

#endif // SHARE_CDS_FILEMAP_HPP
