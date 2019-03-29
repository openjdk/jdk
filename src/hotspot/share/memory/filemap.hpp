/*
 * Copyright (c) 2003, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_FILEMAP_HPP
#define SHARE_MEMORY_FILEMAP_HPP

#include "classfile/classLoader.hpp"
#include "include/cds.h"
#include "memory/metaspaceShared.hpp"
#include "memory/metaspace.hpp"
#include "memory/universe.hpp"
#include "utilities/align.hpp"

// Layout of the file:
//  header: dump of archive instance plus versioning info, datestamp, etc.
//   [magic # = 0xF00BABA2]
//  ... padding to align on page-boundary
//  read-write space
//  read-only space
//  misc data (block offset table, string table, symbols, dictionary, etc.)
//  tag(666)

static const int JVM_IDENT_MAX = 256;

class SharedClassPathEntry {
  enum {
    modules_image_entry,
    jar_entry,
    signed_jar_entry,
    dir_entry,
    unknown_entry
  };
protected:
  u1     _type;
  time_t _timestamp;          // jar timestamp,  0 if is directory, modules image or other
  long   _filesize;           // jar/jimage file size, -1 if is directory, -2 if other
  Array<char>* _name;
  Array<u1>*   _manifest;

public:
  void init(const char* name, bool is_modules_image, TRAPS);
  void metaspace_pointers_do(MetaspaceClosure* it);
  bool validate(bool is_class_path = true);

  // The _timestamp only gets set for jar files.
  bool has_timestamp() {
    return _timestamp != 0;
  }
  bool is_dir()            { return _type == dir_entry; }
  bool is_modules_image()  { return _type == modules_image_entry; }
  bool is_jar()            { return _type == jar_entry; }
  bool is_signed()         { return _type == signed_jar_entry; }
  void set_is_signed()     {
    _type = signed_jar_entry;
  }
  time_t timestamp() const { return _timestamp; }
  long   filesize()  const { return _filesize; }
  const char* name() const { return _name->data(); }
  const char* manifest() const {
    return (_manifest == NULL) ? NULL : (const char*)_manifest->data();
  }
  int manifest_size() const {
    return (_manifest == NULL) ? 0 : _manifest->length();
  }
  void set_manifest(Array<u1>* manifest) {
    _manifest = manifest;
  }
};

struct ArchiveHeapOopmapInfo {
  address _oopmap;               // bitmap for relocating embedded oops
  size_t  _oopmap_size_in_bits;
};

struct FileMapHeader : public CDSFileMapHeaderBase {
  size_t _alignment;                // how shared archive should be aligned
  int    _obj_alignment;            // value of ObjectAlignmentInBytes
  address _narrow_oop_base;         // compressed oop encoding base
  int    _narrow_oop_shift;         // compressed oop encoding shift
  bool    _compact_strings;         // value of CompactStrings
  uintx  _max_heap_size;            // java max heap size during dumping
  Universe::NARROW_OOP_MODE _narrow_oop_mode; // compressed oop encoding mode
  int     _narrow_klass_shift;      // save narrow klass base and shift
  address _narrow_klass_base;
  char*   _misc_data_patching_start;
  char*   _read_only_tables_start;
  address _cds_i2i_entry_code_buffers;
  size_t  _cds_i2i_entry_code_buffers_size;
  size_t  _core_spaces_size;        // number of bytes allocated by the core spaces
                                    // (mc, md, ro, rw and od).
  MemRegion _heap_reserved;         // reserved region for the entire heap at dump time.

  // The following fields are all sanity checks for whether this archive
  // will function correctly with this JVM and the bootclasspath it's
  // invoked with.
  char  _jvm_ident[JVM_IDENT_MAX];      // identifier for jvm

  // The _paths_misc_info is a variable-size structure that records "miscellaneous"
  // information during dumping. It is generated and validated by the
  // SharedPathsMiscInfo class. See SharedPathsMiscInfo.hpp for
  // detailed description.
  //
  // The _paths_misc_info data is stored as a byte array in the archive file header,
  // immediately after the _header field. This information is used only when
  // checking the validity of the archive and is deallocated after the archive is loaded.
  //
  // Note that the _paths_misc_info does NOT include information for JAR files
  // that existed during dump time. Their information is stored in _shared_path_table.
  int _paths_misc_info_size;

  // The following is a table of all the class path entries that were used
  // during dumping. At run time, we require these files to exist and have the same
  // size/modification time, or else the archive will refuse to load.
  //
  // All of these entries must be JAR files. The dumping process would fail if a non-empty
  // directory was specified in the classpaths. If an empty directory was specified
  // it is checked by the _paths_misc_info as described above.
  //
  // FIXME -- if JAR files in the tail of the list were specified but not used during dumping,
  // they should be removed from this table, to save space and to avoid spurious
  // loading failures during runtime.
  int _shared_path_table_size;
  size_t _shared_path_entry_size;
  Array<u8>* _shared_path_table;

  jshort _app_class_paths_start_index;  // Index of first app classpath entry
  jshort _app_module_paths_start_index; // Index of first module path entry
  jshort _max_used_path_index;          // max path index referenced during CDS dump
  bool   _verify_local;                 // BytecodeVerificationLocal setting
  bool   _verify_remote;                // BytecodeVerificationRemote setting
  bool   _has_platform_or_app_classes;  // Archive contains app classes
  size_t _shared_base_address;          // SharedBaseAddress used at dump time
  bool   _allow_archiving_with_java_agent; // setting of the AllowArchivingWithJavaAgent option

  void set_has_platform_or_app_classes(bool v) {
    _has_platform_or_app_classes = v;
  }
  bool has_platform_or_app_classes() { return _has_platform_or_app_classes; }
  jshort max_used_path_index()       { return _max_used_path_index; }
  jshort app_module_paths_start_index() { return _app_module_paths_start_index; }

  bool validate();
  void populate(FileMapInfo* info, size_t alignment);
  int compute_crc();

  CDSFileMapRegion* space_at(int i) {
    assert(i >= 0 && i < NUM_CDS_REGIONS, "invalid region");
    return &_space[i];
  }
};

class FileMapInfo : public CHeapObj<mtInternal> {
private:
  friend class ManifestStream;
  friend class VMStructs;
  friend struct FileMapHeader;

  bool    _file_open;
  int     _fd;
  size_t  _file_offset;

private:
  static Array<u8>*            _shared_path_table;
  static int                   _shared_path_table_size;
  static size_t                _shared_path_entry_size;
  static bool                  _validating_shared_path_table;

  // FileMapHeader describes the shared space data in the file to be
  // mapped.  This structure gets written to a file.  It is not a class, so
  // that the compilers don't add any compiler-private data to it.

public:
  struct FileMapHeaderBase : public CHeapObj<mtClass> {
    // Need to put something here. Otherwise, in product build, because CHeapObj has no virtual
    // methods, we would get sizeof(FileMapHeaderBase) == 1 with gcc.
    intx _dummy;
  };


  FileMapHeader * _header;

  const char* _full_path;
  char* _paths_misc_info;

  static FileMapInfo* _current_info;
  static bool _heap_pointers_need_patching;

  bool  init_from_file(int fd);
  void  align_file_position();
  bool  validate_header_impl();
  static void metaspace_pointers_do(MetaspaceClosure* it);

public:
  FileMapInfo();
  ~FileMapInfo();

  int    compute_header_crc()         { return _header->compute_crc(); }
  void   set_header_crc(int crc)      { _header->_crc = crc; }
  void   populate_header(size_t alignment);
  bool   validate_header();
  void   invalidate();
  int    version()                    { return _header->_version; }
  size_t alignment()                  { return _header->_alignment; }
  Universe::NARROW_OOP_MODE narrow_oop_mode() { return _header->_narrow_oop_mode; }
  address narrow_oop_base()    const  { return _header->_narrow_oop_base; }
  int     narrow_oop_shift()   const  { return _header->_narrow_oop_shift; }
  uintx   max_heap_size()      const  { return _header->_max_heap_size; }
  address narrow_klass_base()  const  { return _header->_narrow_klass_base; }
  int     narrow_klass_shift() const  { return _header->_narrow_klass_shift; }
  struct  FileMapHeader* header()     { return _header; }
  char*   misc_data_patching_start()          { return _header->_misc_data_patching_start; }
  void set_misc_data_patching_start(char* p)  { _header->_misc_data_patching_start = p; }
  char* read_only_tables_start()              { return _header->_read_only_tables_start; }
  void set_read_only_tables_start(char* p)    { _header->_read_only_tables_start = p; }

  address cds_i2i_entry_code_buffers() {
    return _header->_cds_i2i_entry_code_buffers;
  }
  void set_cds_i2i_entry_code_buffers(address addr) {
    _header->_cds_i2i_entry_code_buffers = addr;
  }
  size_t cds_i2i_entry_code_buffers_size() {
    return _header->_cds_i2i_entry_code_buffers_size;
  }
  void set_cds_i2i_entry_code_buffers_size(size_t s) {
    _header->_cds_i2i_entry_code_buffers_size = s;
  }
  void set_core_spaces_size(size_t s)    {  _header->_core_spaces_size = s; }
  size_t core_spaces_size()              { return _header->_core_spaces_size; }

  static FileMapInfo* current_info() {
    CDS_ONLY(return _current_info;)
    NOT_CDS(return NULL;)
  }

  static void assert_mark(bool check);

  // File manipulation.
  bool  initialize() NOT_CDS_RETURN_(false);
  bool  open_for_read();
  void  open_for_write();
  void  write_header();
  void  write_region(int region, char* base, size_t size,
                     bool read_only, bool allow_exec);
  size_t write_archive_heap_regions(GrowableArray<MemRegion> *heap_mem,
                                    GrowableArray<ArchiveHeapOopmapInfo> *oopmaps,
                                    int first_region_id, int max_num_regions,
                                    bool print_log);
  void  write_bytes(const void* buffer, size_t count);
  void  write_bytes_aligned(const void* buffer, size_t count);
  char* map_region(int i, char** top_ret);
  void  map_heap_regions_impl() NOT_CDS_JAVA_HEAP_RETURN;
  void  map_heap_regions() NOT_CDS_JAVA_HEAP_RETURN;
  void  fixup_mapped_heap_regions() NOT_CDS_JAVA_HEAP_RETURN;
  void  patch_archived_heap_embedded_pointers() NOT_CDS_JAVA_HEAP_RETURN;
  void  patch_archived_heap_embedded_pointers(MemRegion* ranges, int num_ranges,
                                              int first_region_idx) NOT_CDS_JAVA_HEAP_RETURN;
  bool  has_heap_regions()  NOT_CDS_JAVA_HEAP_RETURN_(false);
  MemRegion get_heap_regions_range_with_current_oop_encoding_mode() NOT_CDS_JAVA_HEAP_RETURN_(MemRegion());
  void  unmap_region(int i);
  bool  verify_region_checksum(int i);
  void  close();
  bool  is_open() { return _file_open; }
  ReservedSpace reserve_shared_memory();

  // JVM/TI RedefineClasses() support:
  // Remap the shared readonly space to shared readwrite, private.
  bool  remap_shared_readonly_as_readwrite();

  // Errors.
  static void fail_stop(const char *msg, ...) ATTRIBUTE_PRINTF(1, 2);
  static void fail_continue(const char *msg, ...) ATTRIBUTE_PRINTF(1, 2);

  bool is_in_shared_region(const void* p, int idx) NOT_CDS_RETURN_(false);

  // Stop CDS sharing and unmap CDS regions.
  static void stop_sharing_and_unmap(const char* msg);

  static void allocate_shared_path_table();
  static void check_nonempty_dir_in_shared_path_table();
  bool validate_shared_path_table();
  static void update_shared_classpath(ClassPathEntry *cpe, SharedClassPathEntry* ent, TRAPS);

#if INCLUDE_JVMTI
  static ClassFileStream* open_stream_for_jvmti(InstanceKlass* ik, Handle class_loader, TRAPS);
#endif

  static SharedClassPathEntry* shared_path(int index) {
    if (index < 0) {
      return NULL;
    }
    assert(index < _shared_path_table_size, "sanity");
    char* p = (char*)_shared_path_table->data();
    p += _shared_path_entry_size * index;
    return (SharedClassPathEntry*)p;
  }

  static const char* shared_path_name(int index) {
    assert(index >= 0, "Sanity");
    return shared_path(index)->name();
  }

  static int get_number_of_shared_paths() {
    return _shared_path_table_size;
  }

  char* region_addr(int idx);

 private:
  bool  map_heap_data(MemRegion **heap_mem, int first, int max, int* num,
                      bool is_open = false) NOT_CDS_JAVA_HEAP_RETURN_(false);
  bool  verify_mapped_heap_regions(int first, int num) NOT_CDS_JAVA_HEAP_RETURN_(false);
  void  dealloc_archive_heap_regions(MemRegion* regions, int num, bool is_open) NOT_CDS_JAVA_HEAP_RETURN;

  CDSFileMapRegion* space_at(int i) {
    return _header->space_at(i);
  }

  narrowOop offset_of_space(CDSFileMapRegion* spc) {
    return (narrowOop)(spc->_addr._offset);
  }

  // The starting address of spc, as calculated with CompressedOop::decode_non_null()
  address start_address_as_decoded_with_current_oop_encoding_mode(CDSFileMapRegion* spc) {
    return decode_start_address(spc, true);
  }

  // The starting address of spc, as calculated with HeapShared::decode_from_archive()
  address start_address_as_decoded_from_archive(CDSFileMapRegion* spc) {
    return decode_start_address(spc, false);
  }

  address decode_start_address(CDSFileMapRegion* spc, bool with_current_oop_encoding_mode);

#if INCLUDE_JVMTI
  static ClassPathEntry** _classpath_entries_for_jvmti;
  static ClassPathEntry* get_classpath_entry_for_jvmti(int i, TRAPS);
#endif
};

#endif // SHARE_MEMORY_FILEMAP_HPP
