/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/metaspaceShared.hpp"
#include "include/cds.h"
#include "logging/logLevel.hpp"
#include "memory/allocation.hpp"
#include "oops/array.hpp"
#include "oops/compressedOops.hpp"
#include "utilities/align.hpp"

// To understand the layout of the CDS archive file:
//
// java -Xlog:cds+map=info:file=cds.map:none:filesize=0
// java -Xlog:cds+map=debug:file=cds.map:none:filesize=0
// java -Xlog:cds+map=trace:file=cds.map:none:filesize=0

static const int JVM_IDENT_MAX = 256;

class ArchiveHeapInfo;
class BitMapView;
class CHeapBitMap;
class ClassFileStream;
class ClassLoaderData;
class ClassPathEntry;
class outputStream;

class SharedClassPathEntry : public MetaspaceObj {
  enum {
    modules_image_entry,
    jar_entry,
    dir_entry,
    non_existent_entry,
    unknown_entry
  };

  void set_name(const char* name, TRAPS);

  u1     _type;
  bool   _is_module_path;
  bool   _from_class_path_attr;
  time_t _timestamp;          // jar timestamp,  0 if is directory, modules image or other
  int64_t      _filesize;     // jar/jimage file size, -1 if is directory, -2 if other
  Array<char>* _name;
  Array<u1>*   _manifest;

public:
  SharedClassPathEntry() : _type(0), _is_module_path(false),
                           _from_class_path_attr(false), _timestamp(0),
                           _filesize(0), _name(nullptr), _manifest(nullptr) {}
  static int size() {
    static_assert(is_aligned(sizeof(SharedClassPathEntry), wordSize), "must be");
    return (int)(sizeof(SharedClassPathEntry) / wordSize);
  }
  void init(bool is_modules_image, bool is_module_path, ClassPathEntry* cpe, TRAPS);
  void init_as_non_existent(const char* path, TRAPS);
  void metaspace_pointers_do(MetaspaceClosure* it);
  MetaspaceObj::Type type() const { return SharedClassPathEntryType; }
  bool validate(bool is_class_path = true) const;

  // The _timestamp only gets set for jar files.
  bool has_timestamp() const {
    return _timestamp != 0;
  }
  bool is_dir()           const { return _type == dir_entry; }
  bool is_modules_image() const { return _type == modules_image_entry; }
  bool is_jar()           const { return _type == jar_entry; }
  bool from_class_path_attr() { return _from_class_path_attr; }
  time_t timestamp() const { return _timestamp; }
  const char* name() const;
  const char* manifest() const {
    return (_manifest == nullptr) ? nullptr : (const char*)_manifest->data();
  }
  int manifest_size() const {
    return (_manifest == nullptr) ? 0 : _manifest->length();
  }
  void set_manifest(Array<u1>* manifest) {
    _manifest = manifest;
  }
  bool check_non_existent() const;
  void copy_from(SharedClassPathEntry* ent, ClassLoaderData* loader_data, TRAPS);
  bool in_named_module() {
    return is_modules_image() || // modules image doesn't contain unnamed modules
           _is_module_path;      // module path doesn't contain unnamed modules
  }
};

class SharedPathTable {
  Array<SharedClassPathEntry*>* _entries;
public:
  SharedPathTable() : _entries(nullptr) {}
  SharedPathTable(Array<SharedClassPathEntry*>* entries) : _entries(entries) {}

  void dumptime_init(ClassLoaderData* loader_data, TRAPS);
  void metaspace_pointers_do(MetaspaceClosure* it);

  int size() {
    return _entries == nullptr ? 0 : _entries->length();
  }
  SharedClassPathEntry* path_at(int index) {
    return _entries->at(index);
  }
  Array<SharedClassPathEntry*>* table() {return _entries;}
  void set_table(Array<SharedClassPathEntry*>* table) {_entries = table;}
};


class FileMapRegion: private CDSFileMapRegion {
  BitMapView bitmap_view(bool is_oopmap);
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
  size_t used_aligned()             const; // aligned up to MetaspaceShared::core_region_alignment()
  char*  mapped_base()              const { return _mapped_base; }
  char*  mapped_end()               const { return mapped_base()        + used_aligned(); }
  bool   read_only()                const { return _read_only != 0; }
  bool   allow_exec()               const { return _allow_exec != 0; }
  bool   mapped_from_file()         const { return _mapped_from_file != 0; }
  size_t oopmap_offset()            const { assert_is_heap_region();     return _oopmap_offset; }
  size_t oopmap_size_in_bits()      const { assert_is_heap_region();     return _oopmap_size_in_bits; }

  void set_file_offset(size_t s)     { _file_offset = s; }
  void set_read_only(bool v)         { _read_only = v; }
  void set_mapped_base(char* p)      { _mapped_base = p; }
  void set_mapped_from_file(bool v)  { _mapped_from_file = v; }
  void init(int region_index, size_t mapping_offset, size_t size, bool read_only,
            bool allow_exec, int crc);
  void init_oopmap(size_t offset, size_t size_in_bits);
  void init_ptrmap(size_t offset, size_t size_in_bits);
  BitMapView oopmap_view();
  BitMapView ptrmap_view();
  bool has_ptrmap()                  { return _ptrmap_size_in_bits != 0; }

  bool check_region_crc() const;
  void print(outputStream* st, int region_index);
};

class FileMapHeader: private CDSFileMapHeaderBase {
  friend class CDSConstants;
  friend class VMStructs;

private:
  // The following fields record the states of the VM during dump time.
  // They are compared with the runtime states to see if the archive
  // can be used.
  size_t _core_region_alignment;                  // how shared archive should be aligned
  int    _obj_alignment;                          // value of ObjectAlignmentInBytes
  address _narrow_oop_base;                       // compressed oop encoding base
  int    _narrow_oop_shift;                       // compressed oop encoding shift
  bool   _compact_strings;                        // value of CompactStrings
  uintx  _max_heap_size;                          // java max heap size during dumping
  CompressedOops::Mode _narrow_oop_mode;          // compressed oop encoding mode
  bool    _compressed_oops;                       // save the flag UseCompressedOops
  bool    _compressed_class_ptrs;                 // save the flag UseCompressedClassPointers
  size_t  _cloned_vtables_offset;                 // The address of the first cloned vtable
  size_t  _serialized_data_offset;                // Data accessed using {ReadClosure,WriteClosure}::serialize()
  bool _has_non_jar_in_classpath;                 // non-jar file entry exists in classpath
  unsigned int _common_app_classpath_prefix_size; // size of the common prefix of app class paths
                                                  //    0 if no common prefix exists

  // The following fields are all sanity checks for whether this archive
  // will function correctly with this JVM and the bootclasspath it's
  // invoked with.
  char  _jvm_ident[JVM_IDENT_MAX];  // identifier string of the jvm that created this dump

  // The following is a table of all the boot/app/module path entries that were used
  // during dumping. At run time, we validate these entries according to their
  // SharedClassPathEntry::_type. See:
  //      check_nonempty_dir_in_shared_path_table()
  //      validate_shared_path_table()
  //      validate_non_existent_class_paths()
  size_t _shared_path_table_offset;

  jshort _app_class_paths_start_index;  // Index of first app classpath entry
  jshort _app_module_paths_start_index; // Index of first module path entry
  jshort _max_used_path_index;          // max path index referenced during CDS dump
  int    _num_module_paths;             // number of module path entries
  bool   _verify_local;                 // BytecodeVerificationLocal setting
  bool   _verify_remote;                // BytecodeVerificationRemote setting
  bool   _has_platform_or_app_classes;  // Archive contains app classes
  char*  _requested_base_address;       // Archive relocation is not necessary if we map with this base address.
  char*  _mapped_base_address;          // Actual base address where archive is mapped.

  bool   _allow_archiving_with_java_agent; // setting of the AllowArchivingWithJavaAgent option
  bool   _use_optimized_module_handling;// No module-relation VM options were specified, so we can skip
                                        // some expensive operations.
  bool   _use_full_module_graph;        // Can we use the full archived module graph?
  size_t _ptrmap_size_in_bits;          // Size of pointer relocation bitmap
  size_t _heap_roots_offset;            // Offset of the HeapShared::roots() object, from the bottom
                                        // of the archived heap objects, in bytes.
  char* from_mapped_offset(size_t offset) const {
    return mapped_base_address() + offset;
  }
  void set_as_offset(char* p, size_t *offset);
public:
  // Accessors -- fields declared in GenericCDSFileMapHeader
  unsigned int magic()                            const { return _generic_header._magic;                    }
  int crc()                                       const { return _generic_header._crc;                      }
  int version()                                   const { return _generic_header._version;                  }
  unsigned int header_size()                      const { return _generic_header._header_size;              }
  unsigned int base_archive_name_offset()         const { return _generic_header._base_archive_name_offset; }
  unsigned int base_archive_name_size()           const { return _generic_header._base_archive_name_size;   }
  unsigned int common_app_classpath_prefix_size() const { return _common_app_classpath_prefix_size;         }

  void set_magic(unsigned int m)                            { _generic_header._magic = m;                    }
  void set_crc(int crc_value)                               { _generic_header._crc = crc_value;              }
  void set_version(int v)                                   { _generic_header._version = v;                  }
  void set_header_size(unsigned int s)                      { _generic_header._header_size = s;              }
  void set_base_archive_name_offset(unsigned int s)         { _generic_header._base_archive_name_offset = s; }
  void set_base_archive_name_size(unsigned int s)           { _generic_header._base_archive_name_size = s;   }
  void set_common_app_classpath_prefix_size(unsigned int s) { _common_app_classpath_prefix_size = s;         }

  size_t core_region_alignment()           const { return _core_region_alignment; }
  int obj_alignment()                      const { return _obj_alignment; }
  address narrow_oop_base()                const { return _narrow_oop_base; }
  int narrow_oop_shift()                   const { return _narrow_oop_shift; }
  bool compact_strings()                   const { return _compact_strings; }
  uintx max_heap_size()                    const { return _max_heap_size; }
  CompressedOops::Mode narrow_oop_mode()   const { return _narrow_oop_mode; }
  char* cloned_vtables()                   const { return from_mapped_offset(_cloned_vtables_offset); }
  char* serialized_data()                  const { return from_mapped_offset(_serialized_data_offset); }
  const char* jvm_ident()                  const { return _jvm_ident; }
  char* requested_base_address()           const { return _requested_base_address; }
  char* mapped_base_address()              const { return _mapped_base_address; }
  bool has_platform_or_app_classes()       const { return _has_platform_or_app_classes; }
  bool has_non_jar_in_classpath()          const { return _has_non_jar_in_classpath; }
  size_t ptrmap_size_in_bits()             const { return _ptrmap_size_in_bits; }
  bool compressed_oops()                   const { return _compressed_oops; }
  bool compressed_class_pointers()         const { return _compressed_class_ptrs; }
  size_t heap_roots_offset()               const { return _heap_roots_offset; }
  // FIXME: These should really return int
  jshort max_used_path_index()             const { return _max_used_path_index; }
  jshort app_module_paths_start_index()    const { return _app_module_paths_start_index; }
  jshort app_class_paths_start_index()     const { return _app_class_paths_start_index; }
  int    num_module_paths()                const { return _num_module_paths; }

  void set_has_platform_or_app_classes(bool v)   { _has_platform_or_app_classes = v; }
  void set_cloned_vtables(char* p)               { set_as_offset(p, &_cloned_vtables_offset); }
  void set_serialized_data(char* p)              { set_as_offset(p, &_serialized_data_offset); }
  void set_ptrmap_size_in_bits(size_t s)         { _ptrmap_size_in_bits = s; }
  void set_mapped_base_address(char* p)          { _mapped_base_address = p; }
  void set_heap_roots_offset(size_t n)           { _heap_roots_offset = n; }
  void copy_base_archive_name(const char* name);

  void set_shared_path_table(SharedPathTable table) {
    set_as_offset((char*)table.table(), &_shared_path_table_offset);
  }

  void set_requested_base(char* b) {
    _requested_base_address = b;
    _mapped_base_address = 0;
  }

  SharedPathTable shared_path_table() const {
    return SharedPathTable((Array<SharedClassPathEntry*>*)
                           from_mapped_offset(_shared_path_table_offset));
  }

  bool validate();
  int compute_crc();

  FileMapRegion* region_at(int i) {
    assert(is_valid_region(i), "invalid region");
    return FileMapRegion::cast(&_regions[i]);
  }

  void populate(FileMapInfo *info, size_t core_region_alignment, size_t header_size,
                size_t base_archive_name_size, size_t base_archive_name_offset,
                size_t common_app_classpath_size);
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

  static SharedPathTable       _shared_path_table;
  static bool                  _validating_shared_path_table;

  // FileMapHeader describes the shared space data in the file to be
  // mapped.  This structure gets written to a file.  It is not a class, so
  // that the compilers don't add any compiler-private data to it.

  static FileMapInfo* _current_info;
  static FileMapInfo* _dynamic_archive_info;
  static bool _heap_pointers_need_patching;
  static bool _memory_mapping_failed;
  static GrowableArray<const char*>* _non_existent_class_paths;

public:
  FileMapHeader *header() const       { return _header; }
  static bool get_base_archive_name_from_header(const char* archive_name,
                                                char** base_archive_name);
  static SharedPathTable shared_path_table() {
    return _shared_path_table;
  }

  bool init_from_file(int fd);
  static void metaspace_pointers_do(MetaspaceClosure* it) {
    _shared_path_table.metaspace_pointers_do(it);
  }

  void log_paths(const char* msg, int start_idx, int end_idx);

  FileMapInfo(const char* full_apth, bool is_static);
  ~FileMapInfo();

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
  size_t  heap_roots_offset()  const { return header()->heap_roots_offset(); }
  size_t  core_region_alignment() const { return header()->core_region_alignment(); }

  CompressedOops::Mode narrow_oop_mode()      const { return header()->narrow_oop_mode(); }
  jshort app_module_paths_start_index()       const { return header()->app_module_paths_start_index(); }
  jshort app_class_paths_start_index()        const { return header()->app_class_paths_start_index(); }

  char* cloned_vtables()                      const { return header()->cloned_vtables(); }
  void  set_cloned_vtables(char* p)           const { header()->set_cloned_vtables(p); }
  char* serialized_data()                     const { return header()->serialized_data(); }
  void  set_serialized_data(char* p)          const { header()->set_serialized_data(p); }

  bool  is_file_position_aligned() const;
  void  align_file_position();

  bool is_static()                            const { return _is_static; }
  bool is_mapped()                            const { return _is_mapped; }
  void set_is_mapped(bool v)                        { _is_mapped = v; }
  const char* full_path()                     const { return _full_path; }

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
  bool  initialize() NOT_CDS_RETURN_(false);
  bool  open_for_read();
  void  open_for_write();
  void  write_header();
  void  write_region(int region, char* base, size_t size,
                     bool read_only, bool allow_exec);
  char* write_bitmap_region(const CHeapBitMap* ptrmap, ArchiveHeapInfo* heap_info,
                            size_t &size_in_bytes);
  size_t write_heap_region(ArchiveHeapInfo* heap_info);
  void  write_bytes(const void* buffer, size_t count);
  void  write_bytes_aligned(const void* buffer, size_t count);
  size_t  read_bytes(void* buffer, size_t count);
  static size_t readonly_total();
  MapArchiveResult map_regions(int regions[], int num_regions, char* mapped_base_address, ReservedSpace rs);
  void  unmap_regions(int regions[], int num_regions);
  void  map_or_load_heap_region() NOT_CDS_JAVA_HEAP_RETURN;
  void  fixup_mapped_heap_region() NOT_CDS_JAVA_HEAP_RETURN;
  void  patch_heap_embedded_pointers() NOT_CDS_JAVA_HEAP_RETURN;
  bool  has_heap_region()  NOT_CDS_JAVA_HEAP_RETURN_(false);
  MemRegion get_heap_region_requested_range() NOT_CDS_JAVA_HEAP_RETURN_(MemRegion());
  bool  read_region(int i, char* base, size_t size, bool do_commit);
  char* map_bitmap_region();
  void  unmap_region(int i);
  void  close();
  bool  is_open() { return _file_open; }
  ReservedSpace reserve_shared_memory();

  // JVM/TI RedefineClasses() support:
  // Remap the shared readonly space to shared readwrite, private.
  bool  remap_shared_readonly_as_readwrite();

  static bool memory_mapping_failed() {
    CDS_ONLY(return _memory_mapping_failed;)
    NOT_CDS(return false;)
  }

  static void allocate_shared_path_table(TRAPS);
  static int add_shared_classpaths(int i, const char* which, ClassPathEntry *cpe, TRAPS);
  static void check_nonempty_dir_in_shared_path_table();
  bool check_module_paths();
  bool validate_shared_path_table();
  void validate_non_existent_class_paths();
  static void set_shared_path_table(FileMapInfo* info) {
    _shared_path_table = info->header()->shared_path_table();
  }
  static void update_jar_manifest(ClassPathEntry *cpe, SharedClassPathEntry* ent, TRAPS);
  static int num_non_existent_class_paths();
  static void record_non_existent_class_path_entry(const char* path);

#if INCLUDE_JVMTI
  // Caller needs a ResourceMark because parts of the returned cfs are resource-allocated.
  static ClassFileStream* open_stream_for_jvmti(InstanceKlass* ik, Handle class_loader, TRAPS);
#endif

  static SharedClassPathEntry* shared_path(int index) {
    return _shared_path_table.path_at(index);
  }

  static const char* shared_path_name(int index) {
    assert(index >= 0, "Sanity");
    return shared_path(index)->name();
  }

  static int get_number_of_shared_paths() {
    return _shared_path_table.size();
  }

  static int get_module_shared_path_index(Symbol* location) NOT_CDS_RETURN_(-1);

  // The offset of the first core region in the archive, relative to SharedBaseAddress
  size_t mapping_base_offset() const { return first_core_region()->mapping_offset();    }
  // The offset of the (exclusive) end of the last core region in this archive, relative to SharedBaseAddress
  size_t mapping_end_offset()  const { return last_core_region()->mapping_end_offset(); }

  char* mapped_base()    const { return first_core_region()->mapped_base(); }
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

  void print(outputStream* st) const;

  const char* vm_version() {
    return header()->jvm_ident();
  }

 private:
  void  seek_to_position(size_t pos);
  char* skip_first_path_entry(const char* path) NOT_CDS_RETURN_(nullptr);
  int   num_paths(const char* path) NOT_CDS_RETURN_(0);
  bool  check_paths_existence(const char* paths) NOT_CDS_RETURN_(false);
  GrowableArray<const char*>* create_dumptime_app_classpath_array() NOT_CDS_RETURN_(nullptr);
  GrowableArray<const char*>* create_path_array(const char* path) NOT_CDS_RETURN_(nullptr);
  bool  classpath_failure(const char* msg, const char* name) NOT_CDS_RETURN_(false);
  unsigned int longest_common_app_classpath_prefix_len(int num_paths,
                                                       GrowableArray<const char*>* rp_array)
                                                       NOT_CDS_RETURN_(0);
  bool  check_paths(int shared_path_start_idx, int num_paths,
                    GrowableArray<const char*>* rp_array,
                    unsigned int dumptime_prefix_len,
                    unsigned int runtime_prefix_len) NOT_CDS_RETURN_(false);
  bool  validate_boot_class_paths() NOT_CDS_RETURN_(false);
  bool  validate_app_class_paths(int shared_app_paths_len) NOT_CDS_RETURN_(false);
  bool  map_heap_region_impl() NOT_CDS_JAVA_HEAP_RETURN_(false);
  void  dealloc_heap_region() NOT_CDS_JAVA_HEAP_RETURN;
  bool  can_use_heap_region();
  bool  load_heap_region() NOT_CDS_JAVA_HEAP_RETURN_(false);
  bool  map_heap_region() NOT_CDS_JAVA_HEAP_RETURN_(false);
  void  init_heap_region_relocation();
  MapArchiveResult map_region(int i, intx addr_delta, char* mapped_base_address, ReservedSpace rs);
  bool  relocate_pointers_in_core_regions(intx addr_delta);

  static MemRegion _mapped_heap_memregion;

public:
  address heap_region_dumptime_address() NOT_CDS_JAVA_HEAP_RETURN_(nullptr);
  address heap_region_requested_address() NOT_CDS_JAVA_HEAP_RETURN_(nullptr);
  narrowOop encoded_heap_region_dumptime_address();

private:

#if INCLUDE_JVMTI
  static ClassPathEntry** _classpath_entries_for_jvmti;
  static ClassPathEntry* get_classpath_entry_for_jvmti(int i, TRAPS);
#endif
};

#endif // SHARE_CDS_FILEMAP_HPP
