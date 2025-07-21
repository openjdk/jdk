/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotClassLocation.hpp"
#include "cds/aotLogging.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/archiveHeapLoader.inline.hpp"
#include "cds/archiveHeapWriter.hpp"
#include "cds/archiveUtils.inline.hpp"
#include "cds/cds_globals.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/dynamicArchive.hpp"
#include "cds/filemap.hpp"
#include "cds/heapShared.hpp"
#include "cds/metaspaceShared.hpp"
#include "classfile/altHashing.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoader.inline.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "compiler/compilerDefinitions.inline.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "logging/logMessage.hpp"
#include "logging/logStream.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/oopFactory.hpp"
#include "memory/universe.hpp"
#include "nmt/memTracker.hpp"
#include "oops/access.hpp"
#include "oops/compressedOops.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/compressedKlass.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "oops/trainingData.hpp"
#include "oops/typeArrayKlass.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/vm_version.hpp"
#include "utilities/align.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/classpathStream.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/ostream.hpp"
#if INCLUDE_G1GC
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#endif

# include <sys/stat.h>
# include <errno.h>

#ifndef O_BINARY       // if defined (Win32) use binary files.
#define O_BINARY 0     // otherwise do nothing.
#endif

// Fill in the fileMapInfo structure with data about this VM instance.

// This method copies the vm version info into header_version.  If the version is too
// long then a truncated version, which has a hash code appended to it, is copied.
//
// Using a template enables this method to verify that header_version is an array of
// length JVM_IDENT_MAX.  This ensures that the code that writes to the CDS file and
// the code that reads the CDS file will both use the same size buffer.  Hence, will
// use identical truncation.  This is necessary for matching of truncated versions.
template <int N> static void get_header_version(char (&header_version) [N]) {
  assert(N == JVM_IDENT_MAX, "Bad header_version size");

  const char *vm_version = VM_Version::internal_vm_info_string();
  const int version_len = (int)strlen(vm_version);

  memset(header_version, 0, JVM_IDENT_MAX);

  if (version_len < (JVM_IDENT_MAX-1)) {
    strcpy(header_version, vm_version);

  } else {
    // Get the hash value.  Use a static seed because the hash needs to return the same
    // value over multiple jvm invocations.
    uint32_t hash = AltHashing::halfsiphash_32(8191, (const uint8_t*)vm_version, version_len);

    // Truncate the ident, saving room for the 8 hex character hash value.
    strncpy(header_version, vm_version, JVM_IDENT_MAX-9);

    // Append the hash code as eight hex digits.
    os::snprintf_checked(&header_version[JVM_IDENT_MAX-9], 9, "%08x", hash);
    header_version[JVM_IDENT_MAX-1] = 0;  // Null terminate.
  }

  assert(header_version[JVM_IDENT_MAX-1] == 0, "must be");
}

FileMapInfo::FileMapInfo(const char* full_path, bool is_static) :
  _is_static(is_static), _file_open(false), _is_mapped(false), _fd(-1), _file_offset(0),
  _full_path(full_path), _base_archive_name(nullptr), _header(nullptr) {
  if (_is_static) {
    assert(_current_info == nullptr, "must be singleton"); // not thread safe
    _current_info = this;
  } else {
    assert(_dynamic_archive_info == nullptr, "must be singleton"); // not thread safe
    _dynamic_archive_info = this;
  }
}

FileMapInfo::~FileMapInfo() {
  if (_is_static) {
    assert(_current_info == this, "must be singleton"); // not thread safe
    _current_info = nullptr;
  } else {
    assert(_dynamic_archive_info == this, "must be singleton"); // not thread safe
    _dynamic_archive_info = nullptr;
  }

  if (_header != nullptr) {
    os::free(_header);
  }

  if (_file_open) {
    ::close(_fd);
  }
}

void FileMapInfo::free_current_info() {
  assert(CDSConfig::is_dumping_final_static_archive(), "only supported in this mode");
  assert(_current_info != nullptr, "sanity");
  delete _current_info;
  assert(_current_info == nullptr, "sanity"); // Side effect expected from the above "delete" operator.
}

void FileMapInfo::populate_header(size_t core_region_alignment) {
  assert(_header == nullptr, "Sanity check");
  size_t c_header_size;
  size_t header_size;
  size_t base_archive_name_size = 0;
  size_t base_archive_name_offset = 0;
  if (is_static()) {
    c_header_size = sizeof(FileMapHeader);
    header_size = c_header_size;
  } else {
    // dynamic header including base archive name for non-default base archive
    c_header_size = sizeof(DynamicArchiveHeader);
    header_size = c_header_size;

    const char* default_base_archive_name = CDSConfig::default_archive_path();
    const char* current_base_archive_name = CDSConfig::input_static_archive_path();
    if (!os::same_files(current_base_archive_name, default_base_archive_name)) {
      base_archive_name_size = strlen(current_base_archive_name) + 1;
      header_size += base_archive_name_size;
      base_archive_name_offset = c_header_size;
    }
  }
  _header = (FileMapHeader*)os::malloc(header_size, mtInternal);
  memset((void*)_header, 0, header_size);
  _header->populate(this,
                    core_region_alignment,
                    header_size,
                    base_archive_name_size,
                    base_archive_name_offset);
}

void FileMapHeader::populate(FileMapInfo *info, size_t core_region_alignment,
                             size_t header_size, size_t base_archive_name_size,
                             size_t base_archive_name_offset) {
  // 1. We require _generic_header._magic to be at the beginning of the file
  // 2. FileMapHeader also assumes that _generic_header is at the beginning of the file
  assert(offset_of(FileMapHeader, _generic_header) == 0, "must be");
  set_header_size((unsigned int)header_size);
  set_base_archive_name_offset((unsigned int)base_archive_name_offset);
  set_base_archive_name_size((unsigned int)base_archive_name_size);
  if (CDSConfig::is_dumping_dynamic_archive()) {
    set_magic(CDS_DYNAMIC_ARCHIVE_MAGIC);
  } else if (CDSConfig::is_dumping_preimage_static_archive()) {
    set_magic(CDS_PREIMAGE_ARCHIVE_MAGIC);
  } else {
    set_magic(CDS_ARCHIVE_MAGIC);
  }
  set_version(CURRENT_CDS_ARCHIVE_VERSION);

  if (!info->is_static() && base_archive_name_size != 0) {
    // copy base archive name
    copy_base_archive_name(CDSConfig::input_static_archive_path());
  }
  _core_region_alignment = core_region_alignment;
  _obj_alignment = ObjectAlignmentInBytes;
  _compact_strings = CompactStrings;
  _compact_headers = UseCompactObjectHeaders;
  if (CDSConfig::is_dumping_heap()) {
    _narrow_oop_mode = CompressedOops::mode();
    _narrow_oop_base = CompressedOops::base();
    _narrow_oop_shift = CompressedOops::shift();
  }
  _compressed_oops = UseCompressedOops;
  _compressed_class_ptrs = UseCompressedClassPointers;
  if (UseCompressedClassPointers) {
#ifdef _LP64
    _narrow_klass_pointer_bits = CompressedKlassPointers::narrow_klass_pointer_bits();
    _narrow_klass_shift = ArchiveBuilder::precomputed_narrow_klass_shift();
#endif
  } else {
    _narrow_klass_pointer_bits = _narrow_klass_shift = -1;
  }
  // Which JIT compier is used
  _compiler_type = (u1)CompilerConfig::compiler_type();
  _type_profile_level = TypeProfileLevel;
  _type_profile_args_limit = TypeProfileArgsLimit;
  _type_profile_parms_limit = TypeProfileParmsLimit;
  _type_profile_width = TypeProfileWidth;
  _bci_profile_width = BciProfileWidth;
  _profile_traps = ProfileTraps;
  _type_profile_casts = TypeProfileCasts;
  _spec_trap_limit_extra_entries = SpecTrapLimitExtraEntries;
  _max_heap_size = MaxHeapSize;
  _use_optimized_module_handling = CDSConfig::is_using_optimized_module_handling();
  _has_aot_linked_classes = CDSConfig::is_dumping_aot_linked_classes();
  _has_full_module_graph = CDSConfig::is_dumping_full_module_graph();

  // The following fields are for sanity checks for whether this archive
  // will function correctly with this JVM and the bootclasspath it's
  // invoked with.

  // JVM version string ... changes on each build.
  get_header_version(_jvm_ident);

  _verify_local = BytecodeVerificationLocal;
  _verify_remote = BytecodeVerificationRemote;
  _has_platform_or_app_classes = AOTClassLocationConfig::dumptime()->has_platform_or_app_classes();
  _requested_base_address = (char*)SharedBaseAddress;
  _mapped_base_address = (char*)SharedBaseAddress;
  _allow_archiving_with_java_agent = AllowArchivingWithJavaAgent;
}

void FileMapHeader::copy_base_archive_name(const char* archive) {
  assert(base_archive_name_size() != 0, "_base_archive_name_size not set");
  assert(base_archive_name_offset() != 0, "_base_archive_name_offset not set");
  assert(header_size() > sizeof(*this), "_base_archive_name_size not included in header size?");
  memcpy((char*)this + base_archive_name_offset(), archive, base_archive_name_size());
}

void FileMapHeader::print(outputStream* st) {
  ResourceMark rm;

  st->print_cr("- magic:                          0x%08x", magic());
  st->print_cr("- crc:                            0x%08x", crc());
  st->print_cr("- version:                        0x%x", version());
  st->print_cr("- header_size:                    " UINT32_FORMAT, header_size());
  st->print_cr("- base_archive_name_offset:       " UINT32_FORMAT, base_archive_name_offset());
  st->print_cr("- base_archive_name_size:         " UINT32_FORMAT, base_archive_name_size());

  for (int i = 0; i < NUM_CDS_REGIONS; i++) {
    FileMapRegion* r = region_at(i);
    r->print(st, i);
  }
  st->print_cr("============ end regions ======== ");

  st->print_cr("- core_region_alignment:          %zu", _core_region_alignment);
  st->print_cr("- obj_alignment:                  %d", _obj_alignment);
  st->print_cr("- narrow_oop_base:                " INTPTR_FORMAT, p2i(_narrow_oop_base));
  st->print_cr("- narrow_oop_shift                %d", _narrow_oop_shift);
  st->print_cr("- compact_strings:                %d", _compact_strings);
  st->print_cr("- compact_headers:                %d", _compact_headers);
  st->print_cr("- max_heap_size:                  %zu", _max_heap_size);
  st->print_cr("- narrow_oop_mode:                %d", _narrow_oop_mode);
  st->print_cr("- compressed_oops:                %d", _compressed_oops);
  st->print_cr("- compressed_class_ptrs:          %d", _compressed_class_ptrs);
  st->print_cr("- narrow_klass_pointer_bits:      %d", _narrow_klass_pointer_bits);
  st->print_cr("- narrow_klass_shift:             %d", _narrow_klass_shift);
  st->print_cr("- cloned_vtables_offset:          0x%zx", _cloned_vtables_offset);
  st->print_cr("- early_serialized_data_offset:   0x%zx", _early_serialized_data_offset);
  st->print_cr("- serialized_data_offset:         0x%zx", _serialized_data_offset);
  st->print_cr("- jvm_ident:                      %s", _jvm_ident);
  st->print_cr("- class_location_config_offset:   0x%zx", _class_location_config_offset);
  st->print_cr("- verify_local:                   %d", _verify_local);
  st->print_cr("- verify_remote:                  %d", _verify_remote);
  st->print_cr("- has_platform_or_app_classes:    %d", _has_platform_or_app_classes);
  st->print_cr("- requested_base_address:         " INTPTR_FORMAT, p2i(_requested_base_address));
  st->print_cr("- mapped_base_address:            " INTPTR_FORMAT, p2i(_mapped_base_address));
  st->print_cr("- heap_root_segments.roots_count: %d" , _heap_root_segments.roots_count());
  st->print_cr("- heap_root_segments.base_offset: 0x%zx", _heap_root_segments.base_offset());
  st->print_cr("- heap_root_segments.count:       %zu", _heap_root_segments.count());
  st->print_cr("- heap_root_segments.max_size_elems: %d", _heap_root_segments.max_size_in_elems());
  st->print_cr("- heap_root_segments.max_size_bytes: %d", _heap_root_segments.max_size_in_bytes());
  st->print_cr("- _heap_oopmap_start_pos:         %zu", _heap_oopmap_start_pos);
  st->print_cr("- _heap_ptrmap_start_pos:         %zu", _heap_ptrmap_start_pos);
  st->print_cr("- _rw_ptrmap_start_pos:           %zu", _rw_ptrmap_start_pos);
  st->print_cr("- _ro_ptrmap_start_pos:           %zu", _ro_ptrmap_start_pos);
  st->print_cr("- allow_archiving_with_java_agent:%d", _allow_archiving_with_java_agent);
  st->print_cr("- use_optimized_module_handling:  %d", _use_optimized_module_handling);
  st->print_cr("- has_full_module_graph           %d", _has_full_module_graph);
  st->print_cr("- has_aot_linked_classes          %d", _has_aot_linked_classes);
}

bool FileMapInfo::validate_class_location() {
  assert(CDSConfig::is_using_archive(), "runtime only");

  AOTClassLocationConfig* config = header()->class_location_config();
  bool has_extra_module_paths = false;
  if (!config->validate(full_path(), header()->has_aot_linked_classes(), &has_extra_module_paths)) {
    if (PrintSharedArchiveAndExit) {
      MetaspaceShared::set_archive_loading_failed();
      return true;
    } else {
      return false;
    }
  }

  if (header()->has_full_module_graph() && has_extra_module_paths) {
    CDSConfig::stop_using_optimized_module_handling();
    MetaspaceShared::report_loading_error("optimized module handling: disabled because extra module path(s) are specified");
  }

  if (CDSConfig::is_dumping_dynamic_archive()) {
    // Only support dynamic dumping with the usage of the default CDS archive
    // or a simple base archive.
    // If the base layer archive contains additional path component besides
    // the runtime image and the -cp, dynamic dumping is disabled.
    if (config->num_boot_classpaths() > 0) {
      CDSConfig::disable_dumping_dynamic_archive();
      aot_log_warning(aot)(
        "Dynamic archiving is disabled because base layer archive has appended boot classpath");
    }
    if (config->num_module_paths() > 0) {
      if (has_extra_module_paths) {
        CDSConfig::disable_dumping_dynamic_archive();
        aot_log_warning(aot)(
          "Dynamic archiving is disabled because base layer archive has a different module path");
      }
    }
  }

#if INCLUDE_JVMTI
  if (_classpath_entries_for_jvmti != nullptr) {
    os::free(_classpath_entries_for_jvmti);
  }
  size_t sz = sizeof(ClassPathEntry*) * AOTClassLocationConfig::runtime()->length();
  _classpath_entries_for_jvmti = (ClassPathEntry**)os::malloc(sz, mtClass);
  memset((void*)_classpath_entries_for_jvmti, 0, sz);
#endif

  return true;
}

// A utility class for reading/validating the GenericCDSFileMapHeader portion of
// a CDS archive's header. The file header of all CDS archives with versions from
// CDS_GENERIC_HEADER_SUPPORTED_MIN_VERSION (12) are guaranteed to always start
// with GenericCDSFileMapHeader. This makes it possible to read important information
// from a CDS archive created by a different version of HotSpot, so that we can
// automatically regenerate the archive as necessary (JDK-8261455).
class FileHeaderHelper {
  int _fd;
  bool _is_valid;
  bool _is_static;
  GenericCDSFileMapHeader* _header;
  const char* _archive_name;
  const char* _base_archive_name;

public:
  FileHeaderHelper(const char* archive_name, bool is_static) {
    _fd = -1;
    _is_valid = false;
    _header = nullptr;
    _base_archive_name = nullptr;
    _archive_name = archive_name;
    _is_static = is_static;
  }

  ~FileHeaderHelper() {
    if (_header != nullptr) {
      FREE_C_HEAP_ARRAY(char, _header);
    }
    if (_fd != -1) {
      ::close(_fd);
    }
  }

  bool initialize() {
    assert(_archive_name != nullptr, "Archive name is null");
    _fd = os::open(_archive_name, O_RDONLY | O_BINARY, 0);
    if (_fd < 0) {
      MetaspaceShared::report_loading_error("Specified %s not found (%s)", CDSConfig::type_of_archive_being_loaded(), _archive_name);
      return false;
    }
    return initialize(_fd);
  }

  // for an already opened file, do not set _fd
  bool initialize(int fd) {
    assert(_archive_name != nullptr, "Archive name is null");
    assert(fd != -1, "Archive must be opened already");
    // First read the generic header so we know the exact size of the actual header.
    const char* file_type = CDSConfig::type_of_archive_being_loaded();
    GenericCDSFileMapHeader gen_header;
    size_t size = sizeof(GenericCDSFileMapHeader);
    os::lseek(fd, 0, SEEK_SET);
    size_t n = ::read(fd, (void*)&gen_header, (unsigned int)size);
    if (n != size) {
      aot_log_warning(aot)("Unable to read generic CDS file map header from %s", file_type);
      return false;
    }

    if (gen_header._magic != CDS_ARCHIVE_MAGIC &&
        gen_header._magic != CDS_DYNAMIC_ARCHIVE_MAGIC &&
        gen_header._magic != CDS_PREIMAGE_ARCHIVE_MAGIC) {
      aot_log_warning(aot)("The %s has a bad magic number: %#x", file_type, gen_header._magic);
      return false;
    }

    if (gen_header._version < CDS_GENERIC_HEADER_SUPPORTED_MIN_VERSION) {
      aot_log_warning(aot)("Cannot handle %s version 0x%x. Must be at least 0x%x.",
                       file_type, gen_header._version, CDS_GENERIC_HEADER_SUPPORTED_MIN_VERSION);
      return false;
    }

    if (gen_header._version !=  CURRENT_CDS_ARCHIVE_VERSION) {
      aot_log_warning(aot)("The %s version 0x%x does not match the required version 0x%x.",
                       file_type, gen_header._version, CURRENT_CDS_ARCHIVE_VERSION);
    }

    size_t filelen = os::lseek(fd, 0, SEEK_END);
    if (gen_header._header_size >= filelen) {
      aot_log_warning(aot)("Archive file header larger than archive file");
      return false;
    }

    // Read the actual header and perform more checks
    size = gen_header._header_size;
    _header = (GenericCDSFileMapHeader*)NEW_C_HEAP_ARRAY(char, size, mtInternal);
    os::lseek(fd, 0, SEEK_SET);
    n = ::read(fd, (void*)_header, (unsigned int)size);
    if (n != size) {
      aot_log_warning(aot)("Unable to read file map header from %s", file_type);
      return false;
    }

    if (!check_header_crc()) {
      return false;
    }

    if (!check_and_init_base_archive_name()) {
      return false;
    }

    // All fields in the GenericCDSFileMapHeader has been validated.
    _is_valid = true;
    return true;
  }

  GenericCDSFileMapHeader* get_generic_file_header() {
    assert(_header != nullptr && _is_valid, "must be a valid archive file");
    return _header;
  }

  const char* base_archive_name() {
    assert(_header != nullptr && _is_valid, "must be a valid archive file");
    return _base_archive_name;
  }

  bool is_static_archive() const {
    return _header->_magic == CDS_ARCHIVE_MAGIC;
  }

  bool is_dynamic_archive() const {
    return _header->_magic == CDS_DYNAMIC_ARCHIVE_MAGIC;
  }

  bool is_preimage_static_archive() const {
    return _header->_magic == CDS_PREIMAGE_ARCHIVE_MAGIC;
  }

 private:
  bool check_header_crc() const {
    if (VerifySharedSpaces) {
      FileMapHeader* header = (FileMapHeader*)_header;
      int actual_crc = header->compute_crc();
      if (actual_crc != header->crc()) {
        aot_log_info(aot)("_crc expected: %d", header->crc());
        aot_log_info(aot)("       actual: %d", actual_crc);
        aot_log_warning(aot)("Header checksum verification failed.");
        return false;
      }
    }
    return true;
  }

  bool check_and_init_base_archive_name() {
    unsigned int name_offset = _header->_base_archive_name_offset;
    unsigned int name_size   = _header->_base_archive_name_size;
    unsigned int header_size = _header->_header_size;

    if (name_offset + name_size < name_offset) {
      aot_log_warning(aot)("base_archive_name offset/size overflow: " UINT32_FORMAT "/" UINT32_FORMAT,
                                 name_offset, name_size);
      return false;
    }

    if (is_static_archive() || is_preimage_static_archive()) {
      if (name_offset != 0) {
        aot_log_warning(aot)("static shared archive must have zero _base_archive_name_offset");
        return false;
      }
      if (name_size != 0) {
        aot_log_warning(aot)("static shared archive must have zero _base_archive_name_size");
        return false;
      }
    } else {
      assert(is_dynamic_archive(), "must be");
      if ((name_size == 0 && name_offset != 0) ||
          (name_size != 0 && name_offset == 0)) {
        // If either is zero, both must be zero. This indicates that we are using the default base archive.
        aot_log_warning(aot)("Invalid base_archive_name offset/size: " UINT32_FORMAT "/" UINT32_FORMAT,
                                   name_offset, name_size);
        return false;
      }
      if (name_size > 0) {
        if (name_offset + name_size > header_size) {
          aot_log_warning(aot)("Invalid base_archive_name offset/size (out of range): "
                                     UINT32_FORMAT " + " UINT32_FORMAT " > " UINT32_FORMAT ,
                                     name_offset, name_size, header_size);
          return false;
        }
        const char* name = ((const char*)_header) + _header->_base_archive_name_offset;
        if (name[name_size - 1] != '\0' || strlen(name) != name_size - 1) {
          aot_log_warning(aot)("Base archive name is damaged");
          return false;
        }
        if (!os::file_exists(name)) {
          aot_log_warning(aot)("Base archive %s does not exist", name);
          return false;
        }
        _base_archive_name = name;
      }
    }

    return true;
  }
};

// Return value:
// false:
//      <archive_name> is not a valid archive. *base_archive_name is set to null.
// true && (*base_archive_name) == nullptr:
//      <archive_name> is a valid static archive.
// true && (*base_archive_name) != nullptr:
//      <archive_name> is a valid dynamic archive.
bool FileMapInfo::get_base_archive_name_from_header(const char* archive_name,
                                                    const char** base_archive_name) {
  FileHeaderHelper file_helper(archive_name, false);
  *base_archive_name = nullptr;

  if (!file_helper.initialize()) {
    return false;
  }
  GenericCDSFileMapHeader* header = file_helper.get_generic_file_header();
  switch (header->_magic) {
  case CDS_PREIMAGE_ARCHIVE_MAGIC:
    return false; // This is a binary config file, not a proper archive
  case CDS_DYNAMIC_ARCHIVE_MAGIC:
    break;
  default:
    assert(header->_magic == CDS_ARCHIVE_MAGIC, "must be");
    if (AutoCreateSharedArchive) {
     aot_log_warning(aot)("AutoCreateSharedArchive is ignored because %s is a static archive", archive_name);
    }
    return true;
  }

  const char* base = file_helper.base_archive_name();
  if (base == nullptr) {
    *base_archive_name = CDSConfig::default_archive_path();
  } else {
    *base_archive_name = os::strdup_check_oom(base);
  }

  return true;
}

bool FileMapInfo::is_preimage_static_archive(const char* file) {
  FileHeaderHelper file_helper(file, false);
  if (!file_helper.initialize()) {
    return false;
  }
  return file_helper.is_preimage_static_archive();
}

// Read the FileMapInfo information from the file.

bool FileMapInfo::init_from_file(int fd) {
  FileHeaderHelper file_helper(_full_path, _is_static);
  if (!file_helper.initialize(fd)) {
    aot_log_warning(aot)("Unable to read the file header.");
    return false;
  }
  GenericCDSFileMapHeader* gen_header = file_helper.get_generic_file_header();

  const char* file_type = CDSConfig::type_of_archive_being_loaded();
  if (_is_static) {
    if ((gen_header->_magic == CDS_ARCHIVE_MAGIC) ||
        (gen_header->_magic == CDS_PREIMAGE_ARCHIVE_MAGIC && CDSConfig::is_dumping_final_static_archive())) {
      // Good
    } else {
      if (CDSConfig::new_aot_flags_used()) {
        aot_log_warning(aot)("Not a valid %s (%s)", file_type, _full_path);
      } else {
        aot_log_warning(aot)("Not a base shared archive: %s", _full_path);
      }
      return false;
    }
  } else {
    if (gen_header->_magic != CDS_DYNAMIC_ARCHIVE_MAGIC) {
      aot_log_warning(aot)("Not a top shared archive: %s", _full_path);
      return false;
    }
  }

  _header = (FileMapHeader*)os::malloc(gen_header->_header_size, mtInternal);
  os::lseek(fd, 0, SEEK_SET); // reset to begin of the archive
  size_t size = gen_header->_header_size;
  size_t n = ::read(fd, (void*)_header, (unsigned int)size);
  if (n != size) {
    aot_log_warning(aot)("Failed to read file header from the top archive file\n");
    return false;
  }

  if (header()->version() != CURRENT_CDS_ARCHIVE_VERSION) {
    aot_log_info(aot)("_version expected: 0x%x", CURRENT_CDS_ARCHIVE_VERSION);
    aot_log_info(aot)("           actual: 0x%x", header()->version());
    aot_log_warning(aot)("The %s has the wrong version.", file_type);
    return false;
  }

  unsigned int base_offset = header()->base_archive_name_offset();
  unsigned int name_size = header()->base_archive_name_size();
  unsigned int header_size = header()->header_size();
  if (base_offset != 0 && name_size != 0) {
    if (header_size != base_offset + name_size) {
      aot_log_info(aot)("_header_size: " UINT32_FORMAT, header_size);
      aot_log_info(aot)("base_archive_name_size: " UINT32_FORMAT, header()->base_archive_name_size());
      aot_log_info(aot)("base_archive_name_offset: " UINT32_FORMAT, header()->base_archive_name_offset());
      aot_log_warning(aot)("The %s has an incorrect header size.", file_type);
      return false;
    }
  }

  const char* actual_ident = header()->jvm_ident();

  if (actual_ident[JVM_IDENT_MAX-1] != 0) {
    aot_log_warning(aot)("JVM version identifier is corrupted.");
    return false;
  }

  char expected_ident[JVM_IDENT_MAX];
  get_header_version(expected_ident);
  if (strncmp(actual_ident, expected_ident, JVM_IDENT_MAX-1) != 0) {
    aot_log_info(aot)("_jvm_ident expected: %s", expected_ident);
    aot_log_info(aot)("             actual: %s", actual_ident);
    aot_log_warning(aot)("The %s was created by a different"
                  " version or build of HotSpot", file_type);
    return false;
  }

  _file_offset = header()->header_size(); // accounts for the size of _base_archive_name

  size_t len = os::lseek(fd, 0, SEEK_END);

  for (int i = 0; i < MetaspaceShared::n_regions; i++) {
    FileMapRegion* r = region_at(i);
    if (r->file_offset() > len || len - r->file_offset() < r->used()) {
      aot_log_warning(aot)("The %s has been truncated.", file_type);
      return false;
    }
  }

  return true;
}

void FileMapInfo::seek_to_position(size_t pos) {
  if (os::lseek(_fd, (long)pos, SEEK_SET) < 0) {
    aot_log_error(aot)("Unable to seek to position %zu", pos);
    MetaspaceShared::unrecoverable_loading_error();
  }
}

// Read the FileMapInfo information from the file.
bool FileMapInfo::open_for_read() {
  if (_file_open) {
    return true;
  }
  const char* file_type = CDSConfig::type_of_archive_being_loaded();
  const char* info = CDSConfig::is_dumping_final_static_archive() ?
    "AOTConfiguration file " : "";
  aot_log_info(aot)("trying to map %s%s", info, _full_path);
  int fd = os::open(_full_path, O_RDONLY | O_BINARY, 0);
  if (fd < 0) {
    if (errno == ENOENT) {
      aot_log_info(aot)("Specified %s not found (%s)", file_type, _full_path);
    } else {
      aot_log_warning(aot)("Failed to open %s (%s)", file_type,
                    os::strerror(errno));
    }
    return false;
  } else {
    aot_log_info(aot)("Opened %s %s.", file_type, _full_path);
  }

  _fd = fd;
  _file_open = true;
  return true;
}

// Write the FileMapInfo information to the file.

void FileMapInfo::open_as_output() {
  if (CDSConfig::new_aot_flags_used()) {
    if (CDSConfig::is_dumping_preimage_static_archive()) {
      log_info(aot)("Writing binary AOTConfiguration file: %s",  _full_path);
    } else {
      log_info(aot)("Writing AOTCache file: %s",  _full_path);
    }
  } else {
    aot_log_info(aot)("Dumping shared data to file: %s", _full_path);
  }

#ifdef _WINDOWS  // On Windows, need WRITE permission to remove the file.
  chmod(_full_path, _S_IREAD | _S_IWRITE);
#endif

  // Use remove() to delete the existing file because, on Unix, this will
  // allow processes that have it open continued access to the file.
  remove(_full_path);
  int fd = os::open(_full_path, O_RDWR | O_CREAT | O_TRUNC | O_BINARY, 0666);
  if (fd < 0) {
    aot_log_error(aot)("Unable to create %s %s: (%s).", CDSConfig::type_of_archive_being_written(), _full_path,
                   os::strerror(errno));
    MetaspaceShared::writing_error();
    return;
  }
  _fd = fd;
  _file_open = true;

  // Seek past the header. We will write the header after all regions are written
  // and their CRCs computed.
  size_t header_bytes = header()->header_size();

  header_bytes = align_up(header_bytes, MetaspaceShared::core_region_alignment());
  _file_offset = header_bytes;
  seek_to_position(_file_offset);
}

// Write the header to the file, seek to the next allocation boundary.

void FileMapInfo::write_header() {
  _file_offset = 0;
  seek_to_position(_file_offset);
  assert(is_file_position_aligned(), "must be");
  write_bytes(header(), header()->header_size());
}

size_t FileMapRegion::used_aligned() const {
  return align_up(used(), MetaspaceShared::core_region_alignment());
}

void FileMapRegion::init(int region_index, size_t mapping_offset, size_t size, bool read_only,
                         bool allow_exec, int crc) {
  _is_heap_region = HeapShared::is_heap_region(region_index);
  _is_bitmap_region = (region_index == MetaspaceShared::bm);
  _mapping_offset = mapping_offset;
  _used = size;
  _read_only = read_only;
  _allow_exec = allow_exec;
  _crc = crc;
  _mapped_from_file = false;
  _mapped_base = nullptr;
  _in_reserved_space = false;
}

void FileMapRegion::init_oopmap(size_t offset, size_t size_in_bits) {
  _oopmap_offset = offset;
  _oopmap_size_in_bits = size_in_bits;
}

void FileMapRegion::init_ptrmap(size_t offset, size_t size_in_bits) {
  _ptrmap_offset = offset;
  _ptrmap_size_in_bits = size_in_bits;
}

bool FileMapRegion::check_region_crc(char* base) const {
  // This function should be called after the region has been properly
  // loaded into memory via FileMapInfo::map_region() or FileMapInfo::read_region().
  // I.e., this->mapped_base() must be valid.
  size_t sz = used();
  if (sz == 0) {
    return true;
  }

  assert(base != nullptr, "must be initialized");
  int crc = ClassLoader::crc32(0, base, (jint)sz);
  if (crc != this->crc()) {
    aot_log_warning(aot)("Checksum verification failed.");
    return false;
  }
  return true;
}

static const char* region_name(int region_index) {
  static const char* names[] = {
    "rw", "ro", "bm", "hp", "ac"
  };
  const int num_regions = sizeof(names)/sizeof(names[0]);
  assert(0 <= region_index && region_index < num_regions, "sanity");

  return names[region_index];
}

BitMapView FileMapInfo::bitmap_view(int region_index, bool is_oopmap) {
  FileMapRegion* r = region_at(region_index);
  char* bitmap_base = is_static() ? FileMapInfo::current_info()->map_bitmap_region() : FileMapInfo::dynamic_info()->map_bitmap_region();
  bitmap_base += is_oopmap ? r->oopmap_offset() : r->ptrmap_offset();
  size_t size_in_bits = is_oopmap ? r->oopmap_size_in_bits() : r->ptrmap_size_in_bits();

  aot_log_debug(aot, reloc)("mapped %s relocation %smap @ " INTPTR_FORMAT " (%zu bits)",
                        region_name(region_index), is_oopmap ? "oop" : "ptr",
                        p2i(bitmap_base), size_in_bits);

  return BitMapView((BitMap::bm_word_t*)(bitmap_base), size_in_bits);
}

BitMapView FileMapInfo::oopmap_view(int region_index) {
    return bitmap_view(region_index, /*is_oopmap*/true);
  }

BitMapView FileMapInfo::ptrmap_view(int region_index) {
  return bitmap_view(region_index, /*is_oopmap*/false);
}

void FileMapRegion::print(outputStream* st, int region_index) {
  st->print_cr("============ region ============= %d \"%s\"", region_index, region_name(region_index));
  st->print_cr("- crc:                            0x%08x", _crc);
  st->print_cr("- read_only:                      %d", _read_only);
  st->print_cr("- allow_exec:                     %d", _allow_exec);
  st->print_cr("- is_heap_region:                 %d", _is_heap_region);
  st->print_cr("- is_bitmap_region:               %d", _is_bitmap_region);
  st->print_cr("- mapped_from_file:               %d", _mapped_from_file);
  st->print_cr("- file_offset:                    0x%zx", _file_offset);
  st->print_cr("- mapping_offset:                 0x%zx", _mapping_offset);
  st->print_cr("- used:                           %zu", _used);
  st->print_cr("- oopmap_offset:                  0x%zx", _oopmap_offset);
  st->print_cr("- oopmap_size_in_bits:            %zu", _oopmap_size_in_bits);
  st->print_cr("- ptrmap_offset:                  0x%zx", _ptrmap_offset);
  st->print_cr("- ptrmap_size_in_bits:            %zu", _ptrmap_size_in_bits);
  st->print_cr("- mapped_base:                    " INTPTR_FORMAT, p2i(_mapped_base));
}

void FileMapInfo::write_region(int region, char* base, size_t size,
                               bool read_only, bool allow_exec) {
  assert(CDSConfig::is_dumping_archive(), "sanity");

  FileMapRegion* r = region_at(region);
  char* requested_base;
  size_t mapping_offset = 0;

  if (region == MetaspaceShared::bm) {
    requested_base = nullptr; // always null for bm region
  } else if (size == 0) {
    // This is an unused region (e.g., a heap region when !INCLUDE_CDS_JAVA_HEAP)
    requested_base = nullptr;
  } else if (HeapShared::is_heap_region(region)) {
    assert(CDSConfig::is_dumping_heap(), "sanity");
#if INCLUDE_CDS_JAVA_HEAP
    assert(!CDSConfig::is_dumping_dynamic_archive(), "must be");
    requested_base = (char*)ArchiveHeapWriter::requested_address();
    if (UseCompressedOops) {
      mapping_offset = (size_t)((address)requested_base - CompressedOops::base());
      assert((mapping_offset >> CompressedOops::shift()) << CompressedOops::shift() == mapping_offset, "must be");
    } else {
      mapping_offset = 0; // not used with !UseCompressedOops
    }
#endif // INCLUDE_CDS_JAVA_HEAP
  } else {
    char* requested_SharedBaseAddress = (char*)MetaspaceShared::requested_base_address();
    requested_base = ArchiveBuilder::current()->to_requested(base);
    assert(requested_base >= requested_SharedBaseAddress, "must be");
    mapping_offset = requested_base - requested_SharedBaseAddress;
  }

  r->set_file_offset(_file_offset);
  int crc = ClassLoader::crc32(0, base, (jint)size);
  if (size > 0) {
    aot_log_info(aot)("Shared file region (%s) %d: %8zu"
                   " bytes, addr " INTPTR_FORMAT " file offset 0x%08" PRIxPTR
                   " crc 0x%08x",
                   region_name(region), region, size, p2i(requested_base), _file_offset, crc);
  } else {
    aot_log_info(aot)("Shared file region (%s) %d: %8zu"
                   " bytes", region_name(region), region, size);
  }

  r->init(region, mapping_offset, size, read_only, allow_exec, crc);

  if (base != nullptr) {
    write_bytes_aligned(base, size);
  }
}

static size_t write_bitmap(const CHeapBitMap* map, char* output, size_t offset) {
  size_t size_in_bytes = map->size_in_bytes();
  map->write_to((BitMap::bm_word_t*)(output + offset), size_in_bytes);
  return offset + size_in_bytes;
}

// The sorting code groups the objects with non-null oop/ptrs together.
// Relevant bitmaps then have lots of leading and trailing zeros, which
// we do not have to store.
size_t FileMapInfo::remove_bitmap_zeros(CHeapBitMap* map) {
  BitMap::idx_t first_set = map->find_first_set_bit(0);
  BitMap::idx_t last_set  = map->find_last_set_bit(0);
  size_t old_size = map->size();

  // Slice and resize bitmap
  map->truncate(first_set, last_set + 1);

  assert(map->at(0), "First bit should be set");
  assert(map->at(map->size() - 1), "Last bit should be set");
  assert(map->size() <= old_size, "sanity");

  return first_set;
}

char* FileMapInfo::write_bitmap_region(CHeapBitMap* rw_ptrmap, CHeapBitMap* ro_ptrmap, ArchiveHeapInfo* heap_info,
                                       size_t &size_in_bytes) {
  size_t removed_rw_leading_zeros = remove_bitmap_zeros(rw_ptrmap);
  size_t removed_ro_leading_zeros = remove_bitmap_zeros(ro_ptrmap);
  header()->set_rw_ptrmap_start_pos(removed_rw_leading_zeros);
  header()->set_ro_ptrmap_start_pos(removed_ro_leading_zeros);
  size_in_bytes = rw_ptrmap->size_in_bytes() + ro_ptrmap->size_in_bytes();

  if (heap_info->is_used()) {
    // Remove leading and trailing zeros
    size_t removed_oop_leading_zeros = remove_bitmap_zeros(heap_info->oopmap());
    size_t removed_ptr_leading_zeros = remove_bitmap_zeros(heap_info->ptrmap());
    header()->set_heap_oopmap_start_pos(removed_oop_leading_zeros);
    header()->set_heap_ptrmap_start_pos(removed_ptr_leading_zeros);

    size_in_bytes += heap_info->oopmap()->size_in_bytes();
    size_in_bytes += heap_info->ptrmap()->size_in_bytes();
  }

  // The bitmap region contains up to 4 parts:
  // rw_ptrmap:           metaspace pointers inside the read-write region
  // ro_ptrmap:           metaspace pointers inside the read-only region
  // heap_info->oopmap(): Java oop pointers in the heap region
  // heap_info->ptrmap(): metaspace pointers in the heap region
  char* buffer = NEW_C_HEAP_ARRAY(char, size_in_bytes, mtClassShared);
  size_t written = 0;

  region_at(MetaspaceShared::rw)->init_ptrmap(0, rw_ptrmap->size());
  written = write_bitmap(rw_ptrmap, buffer, written);

  region_at(MetaspaceShared::ro)->init_ptrmap(written, ro_ptrmap->size());
  written = write_bitmap(ro_ptrmap, buffer, written);

  if (heap_info->is_used()) {
    FileMapRegion* r = region_at(MetaspaceShared::hp);

    r->init_oopmap(written, heap_info->oopmap()->size());
    written = write_bitmap(heap_info->oopmap(), buffer, written);

    r->init_ptrmap(written, heap_info->ptrmap()->size());
    written = write_bitmap(heap_info->ptrmap(), buffer, written);
  }

  write_region(MetaspaceShared::bm, (char*)buffer, size_in_bytes, /*read_only=*/true, /*allow_exec=*/false);
  return buffer;
}

size_t FileMapInfo::write_heap_region(ArchiveHeapInfo* heap_info) {
  char* buffer_start = heap_info->buffer_start();
  size_t buffer_size = heap_info->buffer_byte_size();
  write_region(MetaspaceShared::hp, buffer_start, buffer_size, false, false);
  header()->set_heap_root_segments(heap_info->heap_root_segments());
  return buffer_size;
}

// Dump bytes to file -- at the current file position.

void FileMapInfo::write_bytes(const void* buffer, size_t nbytes) {
  assert(_file_open, "must be");
  if (!os::write(_fd, buffer, nbytes)) {
    // If the shared archive is corrupted, close it and remove it.
    close();
    remove(_full_path);

    if (CDSConfig::is_dumping_preimage_static_archive()) {
      MetaspaceShared::writing_error("Unable to write to AOT configuration file.");
    } else if (CDSConfig::new_aot_flags_used()) {
      MetaspaceShared::writing_error("Unable to write to AOT cache.");
    } else {
      MetaspaceShared::writing_error("Unable to write to shared archive.");
    }
  }
  _file_offset += nbytes;
}

bool FileMapInfo::is_file_position_aligned() const {
  return _file_offset == align_up(_file_offset,
                                  MetaspaceShared::core_region_alignment());
}

// Align file position to an allocation unit boundary.

void FileMapInfo::align_file_position() {
  assert(_file_open, "must be");
  size_t new_file_offset = align_up(_file_offset,
                                    MetaspaceShared::core_region_alignment());
  if (new_file_offset != _file_offset) {
    _file_offset = new_file_offset;
    // Seek one byte back from the target and write a byte to insure
    // that the written file is the correct length.
    _file_offset -= 1;
    seek_to_position(_file_offset);
    char zero = 0;
    write_bytes(&zero, 1);
  }
}


// Dump bytes to file -- at the current file position.

void FileMapInfo::write_bytes_aligned(const void* buffer, size_t nbytes) {
  align_file_position();
  write_bytes(buffer, nbytes);
  align_file_position();
}

// Close the shared archive file.  This does NOT unmap mapped regions.

void FileMapInfo::close() {
  if (_file_open) {
    if (::close(_fd) < 0) {
      MetaspaceShared::unrecoverable_loading_error("Unable to close the shared archive file.");
    }
    _file_open = false;
    _fd = -1;
  }
}

/*
 * Same as os::map_memory() but also pretouches if AlwaysPreTouch is enabled.
 */
static char* map_memory(int fd, const char* file_name, size_t file_offset,
                        char *addr, size_t bytes, bool read_only,
                        bool allow_exec, MemTag mem_tag) {
  char* mem = os::map_memory(fd, file_name, file_offset, addr, bytes,
                             mem_tag, AlwaysPreTouch ? false : read_only,
                             allow_exec);
  if (mem != nullptr && AlwaysPreTouch) {
    os::pretouch_memory(mem, mem + bytes);
  }
  return mem;
}

// JVM/TI RedefineClasses() support:
// Remap the shared readonly space to shared readwrite, private.
bool FileMapInfo::remap_shared_readonly_as_readwrite() {
  int idx = MetaspaceShared::ro;
  FileMapRegion* r = region_at(idx);
  if (!r->read_only()) {
    // the space is already readwrite so we are done
    return true;
  }
  size_t size = r->used_aligned();
  if (!open_for_read()) {
    return false;
  }
  char *addr = r->mapped_base();
  // This path should not be reached for Windows; see JDK-8222379.
  assert(WINDOWS_ONLY(false) NOT_WINDOWS(true), "Don't call on Windows");
  // Replace old mapping with new one that is writable.
  char *base = os::map_memory(_fd, _full_path, r->file_offset(),
                              addr, size, mtNone, false /* !read_only */,
                              r->allow_exec());
  close();
  // These have to be errors because the shared region is now unmapped.
  if (base == nullptr) {
    aot_log_error(aot)("Unable to remap shared readonly space (errno=%d).", errno);
    vm_exit(1);
  }
  if (base != addr) {
    aot_log_error(aot)("Unable to remap shared readonly space (errno=%d).", errno);
    vm_exit(1);
  }
  r->set_read_only(false);
  return true;
}

// Memory map a region in the address space.
static const char* shared_region_name[] = { "ReadWrite", "ReadOnly", "Bitmap", "Heap", "Code" };

MapArchiveResult FileMapInfo::map_regions(int regions[], int num_regions, char* mapped_base_address, ReservedSpace rs) {
  DEBUG_ONLY(FileMapRegion* last_region = nullptr);
  intx addr_delta = mapped_base_address - header()->requested_base_address();

  // Make sure we don't attempt to use header()->mapped_base_address() unless
  // it's been successfully mapped.
  DEBUG_ONLY(header()->set_mapped_base_address((char*)(uintptr_t)0xdeadbeef);)

  for (int i = 0; i < num_regions; i++) {
    int idx = regions[i];
    MapArchiveResult result = map_region(idx, addr_delta, mapped_base_address, rs);
    if (result != MAP_ARCHIVE_SUCCESS) {
      return result;
    }
    FileMapRegion* r = region_at(idx);
    DEBUG_ONLY(if (last_region != nullptr) {
        // Ensure that the OS won't be able to allocate new memory spaces between any mapped
        // regions, or else it would mess up the simple comparison in MetaspaceObj::is_shared().
        assert(r->mapped_base() == last_region->mapped_end(), "must have no gaps");
      }
      last_region = r;)
    aot_log_info(aot)("Mapped %s region #%d at base " INTPTR_FORMAT " top " INTPTR_FORMAT " (%s)", is_static() ? "static " : "dynamic",
                  idx, p2i(r->mapped_base()), p2i(r->mapped_end()),
                  shared_region_name[idx]);

  }

  header()->set_mapped_base_address(header()->requested_base_address() + addr_delta);
  if (addr_delta != 0 && !relocate_pointers_in_core_regions(addr_delta)) {
    return MAP_ARCHIVE_OTHER_FAILURE;
  }

  return MAP_ARCHIVE_SUCCESS;
}

bool FileMapInfo::read_region(int i, char* base, size_t size, bool do_commit) {
  FileMapRegion* r = region_at(i);
  if (do_commit) {
    aot_log_info(aot)("Commit %s region #%d at base " INTPTR_FORMAT " top " INTPTR_FORMAT " (%s)%s",
                  is_static() ? "static " : "dynamic", i, p2i(base), p2i(base + size),
                  shared_region_name[i], r->allow_exec() ? " exec" : "");
    if (!os::commit_memory(base, size, r->allow_exec())) {
      aot_log_error(aot)("Failed to commit %s region #%d (%s)", is_static() ? "static " : "dynamic",
                     i, shared_region_name[i]);
      return false;
    }
  }
  if (os::lseek(_fd, (long)r->file_offset(), SEEK_SET) != (int)r->file_offset() ||
      read_bytes(base, size) != size) {
    return false;
  }

  if (VerifySharedSpaces && !r->check_region_crc(base)) {
    return false;
  }

  r->set_mapped_from_file(false);
  r->set_mapped_base(base);

  return true;
}

MapArchiveResult FileMapInfo::map_region(int i, intx addr_delta, char* mapped_base_address, ReservedSpace rs) {
  assert(!HeapShared::is_heap_region(i), "sanity");
  FileMapRegion* r = region_at(i);
  size_t size = r->used_aligned();
  char *requested_addr = mapped_base_address + r->mapping_offset();
  assert(!is_mapped(), "must be not mapped yet");
  assert(requested_addr != nullptr, "must be specified");

  r->set_mapped_from_file(false);
  r->set_in_reserved_space(false);

  if (MetaspaceShared::use_windows_memory_mapping()) {
    // Windows cannot remap read-only shared memory to read-write when required for
    // RedefineClasses, which is also used by JFR.  Always map windows regions as RW.
    r->set_read_only(false);
  } else if (JvmtiExport::can_modify_any_class() || JvmtiExport::can_walk_any_space() ||
             Arguments::has_jfr_option()) {
    // If a tool agent is in use (debugging enabled), or JFR, we must map the address space RW
    r->set_read_only(false);
  } else if (addr_delta != 0) {
    r->set_read_only(false); // Need to patch the pointers
  }

  if (MetaspaceShared::use_windows_memory_mapping() && rs.is_reserved()) {
    // This is the second time we try to map the archive(s). We have already created a ReservedSpace
    // that covers all the FileMapRegions to ensure all regions can be mapped. However, Windows
    // can't mmap into a ReservedSpace, so we just ::read() the data. We're going to patch all the
    // regions anyway, so there's no benefit for mmap anyway.
    if (!read_region(i, requested_addr, size, /* do_commit = */ true)) {
      MetaspaceShared::report_loading_error("Failed to read %s shared space into reserved space at " INTPTR_FORMAT,
                                            shared_region_name[i], p2i(requested_addr));
      return MAP_ARCHIVE_OTHER_FAILURE; // oom or I/O error.
    } else {
      assert(r->mapped_base() != nullptr, "must be initialized");
    }
  } else {
    // Note that this may either be a "fresh" mapping into unreserved address
    // space (Windows, first mapping attempt), or a mapping into pre-reserved
    // space (Posix). See also comment in MetaspaceShared::map_archives().
    char* base = map_memory(_fd, _full_path, r->file_offset(),
                            requested_addr, size, r->read_only(),
                            r->allow_exec(), mtClassShared);
    if (base != requested_addr) {
      MetaspaceShared::report_loading_error("Unable to map %s shared space at " INTPTR_FORMAT,
                                            shared_region_name[i], p2i(requested_addr));
      _memory_mapping_failed = true;
      return MAP_ARCHIVE_MMAP_FAILURE;
    }

    if (VerifySharedSpaces && !r->check_region_crc(requested_addr)) {
      return MAP_ARCHIVE_OTHER_FAILURE;
    }

    r->set_mapped_from_file(true);
    r->set_mapped_base(requested_addr);
  }

  if (rs.is_reserved()) {
    char* mapped_base = r->mapped_base();
    assert(rs.base() <= mapped_base && mapped_base + size <= rs.end(),
           PTR_FORMAT " <= " PTR_FORMAT " < " PTR_FORMAT " <= " PTR_FORMAT,
           p2i(rs.base()), p2i(mapped_base), p2i(mapped_base + size), p2i(rs.end()));
    r->set_in_reserved_space(rs.is_reserved());
  }
  return MAP_ARCHIVE_SUCCESS;
}

// The return value is the location of the archive relocation bitmap.
char* FileMapInfo::map_bitmap_region() {
  FileMapRegion* r = region_at(MetaspaceShared::bm);
  if (r->mapped_base() != nullptr) {
    return r->mapped_base();
  }
  bool read_only = true, allow_exec = false;
  char* requested_addr = nullptr; // allow OS to pick any location
  char* bitmap_base = map_memory(_fd, _full_path, r->file_offset(),
                                 requested_addr, r->used_aligned(), read_only, allow_exec, mtClassShared);
  if (bitmap_base == nullptr) {
    MetaspaceShared::report_loading_error("failed to map relocation bitmap");
    return nullptr;
  }

  if (VerifySharedSpaces && !r->check_region_crc(bitmap_base)) {
    aot_log_error(aot)("relocation bitmap CRC error");
    if (!os::unmap_memory(bitmap_base, r->used_aligned())) {
      fatal("os::unmap_memory of relocation bitmap failed");
    }
    return nullptr;
  }

  r->set_mapped_from_file(true);
  r->set_mapped_base(bitmap_base);
  aot_log_info(aot)("Mapped %s region #%d at base " INTPTR_FORMAT " top " INTPTR_FORMAT " (%s)",
                is_static() ? "static " : "dynamic",
                MetaspaceShared::bm, p2i(r->mapped_base()), p2i(r->mapped_end()),
                shared_region_name[MetaspaceShared::bm]);
  return bitmap_base;
}

bool FileMapInfo::map_aot_code_region(ReservedSpace rs) {
  FileMapRegion* r = region_at(MetaspaceShared::ac);
  assert(r->used() > 0 && r->used_aligned() == rs.size(), "must be");

  char* requested_base = rs.base();
  assert(requested_base != nullptr, "should be inside code cache");

  char* mapped_base;
  if (MetaspaceShared::use_windows_memory_mapping()) {
    if (!read_region(MetaspaceShared::ac, requested_base, r->used_aligned(), /* do_commit = */ true)) {
      MetaspaceShared::report_loading_error("Failed to read aot code shared space into reserved space at " INTPTR_FORMAT,
                                            p2i(requested_base));
      return false;
    }
    mapped_base = requested_base;
  } else {
    // We do not execute in-place in the AOT code region.
    // AOT code is copied to the CodeCache for execution.
    bool read_only = false, allow_exec = false;
    mapped_base = map_memory(_fd, _full_path, r->file_offset(),
                             requested_base, r->used_aligned(), read_only, allow_exec, mtClassShared);
  }
  if (mapped_base == nullptr) {
    MetaspaceShared::report_loading_error("failed to map aot code region");
    return false;
  } else {
    assert(mapped_base == requested_base, "must be");
    r->set_mapped_from_file(true);
    r->set_mapped_base(mapped_base);
    aot_log_info(aot)("Mapped static  region #%d at base " INTPTR_FORMAT " top " INTPTR_FORMAT " (%s)",
                  MetaspaceShared::ac, p2i(r->mapped_base()), p2i(r->mapped_end()),
                  shared_region_name[MetaspaceShared::ac]);
    return true;
  }
}

class SharedDataRelocationTask : public ArchiveWorkerTask {
private:
  BitMapView* const _rw_bm;
  BitMapView* const _ro_bm;
  SharedDataRelocator* const _rw_reloc;
  SharedDataRelocator* const _ro_reloc;

public:
  SharedDataRelocationTask(BitMapView* rw_bm, BitMapView* ro_bm, SharedDataRelocator* rw_reloc, SharedDataRelocator* ro_reloc) :
                           ArchiveWorkerTask("Shared Data Relocation"),
                           _rw_bm(rw_bm), _ro_bm(ro_bm), _rw_reloc(rw_reloc), _ro_reloc(ro_reloc) {}

  void work(int chunk, int max_chunks) override {
    work_on(chunk, max_chunks, _rw_bm, _rw_reloc);
    work_on(chunk, max_chunks, _ro_bm, _ro_reloc);
  }

  void work_on(int chunk, int max_chunks, BitMapView* bm, SharedDataRelocator* reloc) {
    BitMap::idx_t size  = bm->size();
    BitMap::idx_t start = MIN2(size, size * chunk / max_chunks);
    BitMap::idx_t end   = MIN2(size, size * (chunk + 1) / max_chunks);
    assert(end > start, "Sanity: no empty slices");
    bm->iterate(reloc, start, end);
  }
};

// This is called when we cannot map the archive at the requested[ base address (usually 0x800000000).
// We relocate all pointers in the 2 core regions (ro, rw).
bool FileMapInfo::relocate_pointers_in_core_regions(intx addr_delta) {
  aot_log_debug(aot, reloc)("runtime archive relocation start");
  char* bitmap_base = map_bitmap_region();

  if (bitmap_base == nullptr) {
    return false; // OOM, or CRC check failure
  } else {
    BitMapView rw_ptrmap = ptrmap_view(MetaspaceShared::rw);
    BitMapView ro_ptrmap = ptrmap_view(MetaspaceShared::ro);

    FileMapRegion* rw_region = first_core_region();
    FileMapRegion* ro_region = last_core_region();

    // Patch all pointers inside the RW region
    address rw_patch_base = (address)rw_region->mapped_base();
    address rw_patch_end  = (address)rw_region->mapped_end();

    // Patch all pointers inside the RO region
    address ro_patch_base = (address)ro_region->mapped_base();
    address ro_patch_end  = (address)ro_region->mapped_end();

    // the current value of the pointers to be patched must be within this
    // range (i.e., must be between the requested base address and the address of the current archive).
    // Note: top archive may point to objects in the base archive, but not the other way around.
    address valid_old_base = (address)header()->requested_base_address();
    address valid_old_end  = valid_old_base + mapping_end_offset();

    // after patching, the pointers must point inside this range
    // (the requested location of the archive, as mapped at runtime).
    address valid_new_base = (address)header()->mapped_base_address();
    address valid_new_end  = (address)mapped_end();

    SharedDataRelocator rw_patcher((address*)rw_patch_base + header()->rw_ptrmap_start_pos(), (address*)rw_patch_end, valid_old_base, valid_old_end,
                                valid_new_base, valid_new_end, addr_delta);
    SharedDataRelocator ro_patcher((address*)ro_patch_base + header()->ro_ptrmap_start_pos(), (address*)ro_patch_end, valid_old_base, valid_old_end,
                                valid_new_base, valid_new_end, addr_delta);

    if (AOTCacheParallelRelocation) {
      ArchiveWorkers workers;
      SharedDataRelocationTask task(&rw_ptrmap, &ro_ptrmap, &rw_patcher, &ro_patcher);
      workers.run_task(&task);
    } else {
      rw_ptrmap.iterate(&rw_patcher);
      ro_ptrmap.iterate(&ro_patcher);
    }

    // The MetaspaceShared::bm region will be unmapped in MetaspaceShared::initialize_shared_spaces().

    aot_log_debug(aot, reloc)("runtime archive relocation done");
    return true;
  }
}

size_t FileMapInfo::read_bytes(void* buffer, size_t count) {
  assert(_file_open, "Archive file is not open");
  size_t n = ::read(_fd, buffer, (unsigned int)count);
  if (n != count) {
    // Close the file if there's a problem reading it.
    close();
    return 0;
  }
  _file_offset += count;
  return count;
}

// Get the total size in bytes of a read only region
size_t FileMapInfo::readonly_total() {
  size_t total = 0;
  if (current_info() != nullptr) {
    FileMapRegion* r = FileMapInfo::current_info()->region_at(MetaspaceShared::ro);
    if (r->read_only()) total += r->used();
  }
  if (dynamic_info() != nullptr) {
    FileMapRegion* r = FileMapInfo::dynamic_info()->region_at(MetaspaceShared::ro);
    if (r->read_only()) total += r->used();
  }
  return total;
}

#if INCLUDE_CDS_JAVA_HEAP
MemRegion FileMapInfo::_mapped_heap_memregion;

bool FileMapInfo::has_heap_region() {
  return (region_at(MetaspaceShared::hp)->used() > 0);
}

// Returns the address range of the archived heap region computed using the
// current oop encoding mode. This range may be different than the one seen at
// dump time due to encoding mode differences. The result is used in determining
// if/how these regions should be relocated at run time.
MemRegion FileMapInfo::get_heap_region_requested_range() {
  FileMapRegion* r = region_at(MetaspaceShared::hp);
  size_t size = r->used();
  assert(size > 0, "must have non-empty heap region");

  address start = heap_region_requested_address();
  address end = start + size;
  aot_log_info(aot)("Requested heap region [" INTPTR_FORMAT " - " INTPTR_FORMAT "] = %8zu bytes",
                p2i(start), p2i(end), size);

  return MemRegion((HeapWord*)start, (HeapWord*)end);
}

void FileMapInfo::map_or_load_heap_region() {
  bool success = false;

  if (can_use_heap_region()) {
    if (ArchiveHeapLoader::can_map()) {
      success = map_heap_region();
    } else if (ArchiveHeapLoader::can_load()) {
      success = ArchiveHeapLoader::load_heap_region(this);
    } else {
      if (!UseCompressedOops && !ArchiveHeapLoader::can_map()) {
        MetaspaceShared::report_loading_error("Cannot use CDS heap data. Selected GC not compatible -XX:-UseCompressedOops");
      } else {
        MetaspaceShared::report_loading_error("Cannot use CDS heap data. UseEpsilonGC, UseG1GC, UseSerialGC, UseParallelGC, or UseShenandoahGC are required.");
      }
    }
  }

  if (!success) {
    if (CDSConfig::is_using_aot_linked_classes()) {
      // It's too late to recover -- we have already committed to use the archived metaspace objects, but
      // the archived heap objects cannot be loaded, so we don't have the archived FMG to guarantee that
      // all AOT-linked classes are visible.
      //
      // We get here because the heap is too small. The app will fail anyway. So let's quit.
      aot_log_error(aot)("%s has aot-linked classes but the archived "
                     "heap objects cannot be loaded. Try increasing your heap size.",
                     CDSConfig::type_of_archive_being_loaded());
      MetaspaceShared::unrecoverable_loading_error();
    }
    CDSConfig::stop_using_full_module_graph("archive heap loading failed");
  }
}

bool FileMapInfo::can_use_heap_region() {
  if (!has_heap_region()) {
    return false;
  }
  if (JvmtiExport::should_post_class_file_load_hook() && JvmtiExport::has_early_class_hook_env()) {
    ShouldNotReachHere(); // CDS should have been disabled.
    // The archived objects are mapped at JVM start-up, but we don't know if
    // j.l.String or j.l.Class might be replaced by the ClassFileLoadHook,
    // which would make the archived String or mirror objects invalid. Let's be safe and not
    // use the archived objects. These 2 classes are loaded during the JVMTI "early" stage.
    //
    // If JvmtiExport::has_early_class_hook_env() is false, the classes of some objects
    // in the archived subgraphs may be replaced by the ClassFileLoadHook. But that's OK
    // because we won't install an archived object subgraph if the klass of any of the
    // referenced objects are replaced. See HeapShared::initialize_from_archived_subgraph().
  }

  // We pre-compute narrow Klass IDs with the runtime mapping start intended to be the base, and a shift of
  // ArchiveBuilder::precomputed_narrow_klass_shift. We enforce this encoding at runtime (see
  // CompressedKlassPointers::initialize_for_given_encoding()). Therefore, the following assertions must
  // hold:
  address archive_narrow_klass_base = (address)header()->mapped_base_address();
  const int archive_narrow_klass_pointer_bits = header()->narrow_klass_pointer_bits();
  const int archive_narrow_klass_shift = header()->narrow_klass_shift();

  aot_log_info(aot)("CDS archive was created with max heap size = %zuM, and the following configuration:",
                max_heap_size()/M);
  aot_log_info(aot)("    narrow_klass_base at mapping start address, narrow_klass_pointer_bits = %d, narrow_klass_shift = %d",
                archive_narrow_klass_pointer_bits, archive_narrow_klass_shift);
  aot_log_info(aot)("    narrow_oop_mode = %d, narrow_oop_base = " PTR_FORMAT ", narrow_oop_shift = %d",
                narrow_oop_mode(), p2i(narrow_oop_base()), narrow_oop_shift());
  aot_log_info(aot)("The current max heap size = %zuM, G1HeapRegion::GrainBytes = %zu",
                MaxHeapSize/M, G1HeapRegion::GrainBytes);
  aot_log_info(aot)("    narrow_klass_base = " PTR_FORMAT ", arrow_klass_pointer_bits = %d, narrow_klass_shift = %d",
                p2i(CompressedKlassPointers::base()), CompressedKlassPointers::narrow_klass_pointer_bits(), CompressedKlassPointers::shift());
  aot_log_info(aot)("    narrow_oop_mode = %d, narrow_oop_base = " PTR_FORMAT ", narrow_oop_shift = %d",
                CompressedOops::mode(), p2i(CompressedOops::base()), CompressedOops::shift());
  aot_log_info(aot)("    heap range = [" PTR_FORMAT " - "  PTR_FORMAT "]",
                UseCompressedOops ? p2i(CompressedOops::begin()) :
                                    UseG1GC ? p2i((address)G1CollectedHeap::heap()->reserved().start()) : 0L,
                UseCompressedOops ? p2i(CompressedOops::end()) :
                                    UseG1GC ? p2i((address)G1CollectedHeap::heap()->reserved().end()) : 0L);

  int err = 0;
  if ( archive_narrow_klass_base != CompressedKlassPointers::base() ||
       (err = 1, archive_narrow_klass_pointer_bits != CompressedKlassPointers::narrow_klass_pointer_bits()) ||
       (err = 2, archive_narrow_klass_shift != CompressedKlassPointers::shift()) ) {
    stringStream ss;
    switch (err) {
    case 0:
      ss.print("Unexpected encoding base encountered (" PTR_FORMAT ", expected " PTR_FORMAT ")",
               p2i(CompressedKlassPointers::base()), p2i(archive_narrow_klass_base));
      break;
    case 1:
      ss.print("Unexpected narrow Klass bit length encountered (%d, expected %d)",
               CompressedKlassPointers::narrow_klass_pointer_bits(), archive_narrow_klass_pointer_bits);
      break;
    case 2:
      ss.print("Unexpected narrow Klass shift encountered (%d, expected %d)",
               CompressedKlassPointers::shift(), archive_narrow_klass_shift);
      break;
    default:
      ShouldNotReachHere();
    };
    if (CDSConfig::new_aot_flags_used()) {
      LogTarget(Info, aot) lt;
      if (lt.is_enabled()) {
        LogStream ls(lt);
        ls.print_raw(ss.base());
        header()->print(&ls);
      }
    } else {
      LogTarget(Info, cds) lt;
      if (lt.is_enabled()) {
        LogStream ls(lt);
        ls.print_raw(ss.base());
        header()->print(&ls);
      }
    }
    assert(false, "%s", ss.base());
  }

  return true;
}

// The actual address of this region during dump time.
address FileMapInfo::heap_region_dumptime_address() {
  FileMapRegion* r = region_at(MetaspaceShared::hp);
  assert(CDSConfig::is_using_archive(), "runtime only");
  assert(is_aligned(r->mapping_offset(), sizeof(HeapWord)), "must be");
  if (UseCompressedOops) {
    return /*dumptime*/ (address)((uintptr_t)narrow_oop_base() + r->mapping_offset());
  } else {
    return heap_region_requested_address();
  }
}

// The address where this region can be mapped into the runtime heap without
// patching any of the pointers that are embedded in this region.
address FileMapInfo::heap_region_requested_address() {
  assert(CDSConfig::is_using_archive(), "runtime only");
  FileMapRegion* r = region_at(MetaspaceShared::hp);
  assert(is_aligned(r->mapping_offset(), sizeof(HeapWord)), "must be");
  assert(ArchiveHeapLoader::can_use(), "GC must support mapping or loading");
  if (UseCompressedOops) {
    // We can avoid relocation if each region's offset from the runtime CompressedOops::base()
    // is the same as its offset from the CompressedOops::base() during dumptime.
    // Note that CompressedOops::base() may be different between dumptime and runtime.
    //
    // Example:
    // Dumptime base = 0x1000 and shift is 0. We have a region at address 0x2000. There's a
    // narrowOop P stored in this region that points to an object at address 0x2200.
    // P's encoded value is 0x1200.
    //
    // Runtime base = 0x4000 and shift is also 0. If we map this region at 0x5000, then
    // the value P can remain 0x1200. The decoded address = (0x4000 + (0x1200 << 0)) = 0x5200,
    // which is the runtime location of the referenced object.
    return /*runtime*/ (address)((uintptr_t)CompressedOops::base() + r->mapping_offset());
  } else {
    // This was the hard-coded requested base address used at dump time. With uncompressed oops,
    // the heap range is assigned by the OS so we will most likely have to relocate anyway, no matter
    // what base address was picked at duump time.
    return (address)ArchiveHeapWriter::NOCOOPS_REQUESTED_BASE;
  }
}

bool FileMapInfo::map_heap_region() {
  if (map_heap_region_impl()) {
#ifdef ASSERT
    // The "old" regions must be parsable -- we cannot have any unused space
    // at the start of the lowest G1 region that contains archived objects.
    assert(is_aligned(_mapped_heap_memregion.start(), G1HeapRegion::GrainBytes), "must be");

    // Make sure we map at the very top of the heap - see comments in
    // init_heap_region_relocation().
    MemRegion heap_range = G1CollectedHeap::heap()->reserved();
    assert(heap_range.contains(_mapped_heap_memregion), "must be");

    address heap_end = (address)heap_range.end();
    address mapped_heap_region_end = (address)_mapped_heap_memregion.end();
    assert(heap_end >= mapped_heap_region_end, "must be");
    assert(heap_end - mapped_heap_region_end < (intx)(G1HeapRegion::GrainBytes),
           "must be at the top of the heap to avoid fragmentation");
#endif

    ArchiveHeapLoader::set_mapped();
    return true;
  } else {
    return false;
  }
}

bool FileMapInfo::map_heap_region_impl() {
  assert(UseG1GC, "the following code assumes G1");

  FileMapRegion* r = region_at(MetaspaceShared::hp);
  size_t size = r->used();
  if (size == 0) {
    return false; // no archived java heap data
  }

  size_t word_size = size / HeapWordSize;
  address requested_start = heap_region_requested_address();

  aot_log_info(aot)("Preferred address to map heap data (to avoid relocation) is " INTPTR_FORMAT, p2i(requested_start));

  // allocate from java heap
  HeapWord* start = G1CollectedHeap::heap()->alloc_archive_region(word_size, (HeapWord*)requested_start);
  if (start == nullptr) {
    MetaspaceShared::report_loading_error("UseSharedSpaces: Unable to allocate java heap region for archive heap.");
    return false;
  }

  _mapped_heap_memregion = MemRegion(start, word_size);

  // Map the archived heap data. No need to call MemTracker::record_virtual_memory_tag()
  // for mapped region as it is part of the reserved java heap, which is already recorded.
  char* addr = (char*)_mapped_heap_memregion.start();
  char* base;

  if (MetaspaceShared::use_windows_memory_mapping() || UseLargePages) {
    // With UseLargePages, memory mapping may fail on some OSes if the size is not
    // large page aligned, so let's use read() instead. In this case, the memory region
    // is already commited by G1 so we don't need to commit it again.
    if (!read_region(MetaspaceShared::hp, addr,
                     align_up(_mapped_heap_memregion.byte_size(), os::vm_page_size()),
                     /* do_commit = */ !UseLargePages)) {
      dealloc_heap_region();
      aot_log_error(aot)("Failed to read archived heap region into " INTPTR_FORMAT, p2i(addr));
      return false;
    }
    // Checks for VerifySharedSpaces is already done inside read_region()
    base = addr;
  } else {
    base = map_memory(_fd, _full_path, r->file_offset(),
                      addr, _mapped_heap_memregion.byte_size(), r->read_only(),
                      r->allow_exec(), mtJavaHeap);
    if (base == nullptr || base != addr) {
      dealloc_heap_region();
      MetaspaceShared::report_loading_error("UseSharedSpaces: Unable to map at required address in java heap. "
                                            INTPTR_FORMAT ", size = %zu bytes",
                                            p2i(addr), _mapped_heap_memregion.byte_size());
      return false;
    }

    if (VerifySharedSpaces && !r->check_region_crc(base)) {
      dealloc_heap_region();
      MetaspaceShared::report_loading_error("UseSharedSpaces: mapped heap region is corrupt");
      return false;
    }
  }

  r->set_mapped_base(base);

  // If the requested range is different from the range allocated by GC, then
  // the pointers need to be patched.
  address mapped_start = (address) _mapped_heap_memregion.start();
  ptrdiff_t delta = mapped_start - requested_start;
  if (UseCompressedOops &&
      (narrow_oop_mode() != CompressedOops::mode() ||
       narrow_oop_shift() != CompressedOops::shift())) {
    _heap_pointers_need_patching = true;
  }
  if (delta != 0) {
    _heap_pointers_need_patching = true;
  }
  ArchiveHeapLoader::init_mapped_heap_info(mapped_start, delta, narrow_oop_shift());

  if (_heap_pointers_need_patching) {
    char* bitmap_base = map_bitmap_region();
    if (bitmap_base == nullptr) {
      MetaspaceShared::report_loading_error("CDS heap cannot be used because bitmap region cannot be mapped");
      dealloc_heap_region();
      _heap_pointers_need_patching = false;
      return false;
    }
  }
  aot_log_info(aot)("Heap data mapped at " INTPTR_FORMAT ", size = %8zu bytes",
                p2i(mapped_start), _mapped_heap_memregion.byte_size());
  aot_log_info(aot)("CDS heap data relocation delta = %zd bytes", delta);
  return true;
}

narrowOop FileMapInfo::encoded_heap_region_dumptime_address() {
  assert(CDSConfig::is_using_archive(), "runtime only");
  assert(UseCompressedOops, "sanity");
  FileMapRegion* r = region_at(MetaspaceShared::hp);
  return CompressedOops::narrow_oop_cast(r->mapping_offset() >> narrow_oop_shift());
}

void FileMapInfo::patch_heap_embedded_pointers() {
  if (!ArchiveHeapLoader::is_mapped() || !_heap_pointers_need_patching) {
    return;
  }

  char* bitmap_base = map_bitmap_region();
  assert(bitmap_base != nullptr, "must have already been mapped");

  FileMapRegion* r = region_at(MetaspaceShared::hp);
  ArchiveHeapLoader::patch_embedded_pointers(
      this, _mapped_heap_memregion,
      (address)(region_at(MetaspaceShared::bm)->mapped_base()) + r->oopmap_offset(),
      r->oopmap_size_in_bits());
}

void FileMapInfo::fixup_mapped_heap_region() {
  if (ArchiveHeapLoader::is_mapped()) {
    assert(!_mapped_heap_memregion.is_empty(), "sanity");

    // Populate the archive regions' G1BlockOffsetTables. That ensures
    // fast G1BlockOffsetTable::block_start operations for any given address
    // within the archive regions when trying to find start of an object
    // (e.g. during card table scanning).
    G1CollectedHeap::heap()->populate_archive_regions_bot(_mapped_heap_memregion);
  }
}

// dealloc the archive regions from java heap
void FileMapInfo::dealloc_heap_region() {
  G1CollectedHeap::heap()->dealloc_archive_regions(_mapped_heap_memregion);
}
#endif // INCLUDE_CDS_JAVA_HEAP

void FileMapInfo::unmap_regions(int regions[], int num_regions) {
  for (int r = 0; r < num_regions; r++) {
    int idx = regions[r];
    unmap_region(idx);
  }
}

// Unmap a memory region in the address space.

void FileMapInfo::unmap_region(int i) {
  FileMapRegion* r = region_at(i);
  char* mapped_base = r->mapped_base();
  size_t size = r->used_aligned();

  if (mapped_base != nullptr) {
    if (size > 0 && r->mapped_from_file()) {
      aot_log_info(aot)("Unmapping region #%d at base " INTPTR_FORMAT " (%s)", i, p2i(mapped_base),
                    shared_region_name[i]);
      if (r->in_reserved_space()) {
        // This region was mapped inside a ReservedSpace. Its memory will be freed when the ReservedSpace
        // is released. Zero it so that we don't accidentally read its content.
        aot_log_info(aot)("Region #%d (%s) is in a reserved space, it will be freed when the space is released", i, shared_region_name[i]);
      } else {
        if (!os::unmap_memory(mapped_base, size)) {
          fatal("os::unmap_memory failed");
        }
      }
    }
    r->set_mapped_base(nullptr);
  }
}

void FileMapInfo::assert_mark(bool check) {
  if (!check) {
    MetaspaceShared::unrecoverable_loading_error("Mark mismatch while restoring from shared file.");
  }
}

FileMapInfo* FileMapInfo::_current_info = nullptr;
FileMapInfo* FileMapInfo::_dynamic_archive_info = nullptr;
bool FileMapInfo::_heap_pointers_need_patching = false;
bool FileMapInfo::_memory_mapping_failed = false;

// Open the shared archive file, read and validate the header
// information (version, boot classpath, etc.). If initialization
// fails, shared spaces are disabled and the file is closed.
//
// Validation of the archive is done in two steps:
//
// [1] validate_header() - done here.
// [2] validate_shared_path_table - this is done later, because the table is in the RO
//     region of the archive, which is not mapped yet.
bool FileMapInfo::open_as_input() {
  assert(CDSConfig::is_using_archive(), "UseSharedSpaces expected.");
  assert(Arguments::has_jimage(), "The shared archive file cannot be used with an exploded module build.");

  if (JvmtiExport::should_post_class_file_load_hook() && JvmtiExport::has_early_class_hook_env()) {
    // CDS assumes that no classes resolved in vmClasses::resolve_all()
    // are replaced at runtime by JVMTI ClassFileLoadHook. All of those classes are resolved
    // during the JVMTI "early" stage, so we can still use CDS if
    // JvmtiExport::has_early_class_hook_env() is false.
    MetaspaceShared::report_loading_error("CDS is disabled because early JVMTI ClassFileLoadHook is in use.");
    return false;
  }

  if (!open_for_read() || !init_from_file(_fd) || !validate_header()) {
    if (_is_static) {
      MetaspaceShared::report_loading_error("Loading static archive failed.");
      return false;
    } else {
      MetaspaceShared::report_loading_error("Loading dynamic archive failed.");
      if (AutoCreateSharedArchive) {
        CDSConfig::enable_dumping_dynamic_archive(_full_path);
      }
      return false;
    }
  }

  return true;
}

bool FileMapInfo::validate_aot_class_linking() {
  // These checks need to be done after FileMapInfo::initialize(), which gets called before Universe::heap()
  // is available.
  if (header()->has_aot_linked_classes()) {
    const char* archive_type = CDSConfig::type_of_archive_being_loaded();
    CDSConfig::set_has_aot_linked_classes(true);
    if (JvmtiExport::should_post_class_file_load_hook()) {
      aot_log_error(aot)("%s has aot-linked classes. It cannot be used when JVMTI ClassFileLoadHook is in use.",
                     archive_type);
      return false;
    }
    if (JvmtiExport::has_early_vmstart_env()) {
      aot_log_error(aot)("%s has aot-linked classes. It cannot be used when JVMTI early vm start is in use.",
                     archive_type);
      return false;
    }
    if (!CDSConfig::is_using_full_module_graph()) {
      aot_log_error(aot)("%s has aot-linked classes. It cannot be used when archived full module graph is not used.",
                     archive_type);
      return false;
    }

    const char* prop = Arguments::get_property("java.security.manager");
    if (prop != nullptr && strcmp(prop, "disallow") != 0) {
      aot_log_error(aot)("%s has aot-linked classes. It cannot be used with -Djava.security.manager=%s.",
                     archive_type, prop);
      return false;
    }

#if INCLUDE_JVMTI
    if (Arguments::has_jdwp_agent()) {
      aot_log_error(aot)("%s has aot-linked classes. It cannot be used with JDWP agent", archive_type);
      return false;
    }
#endif
  }

  return true;
}

// The 2 core spaces are RW->RO
FileMapRegion* FileMapInfo::first_core_region() const {
  return region_at(MetaspaceShared::rw);
}

FileMapRegion* FileMapInfo::last_core_region() const {
  return region_at(MetaspaceShared::ro);
}

void FileMapInfo::print(outputStream* st) const {
  header()->print(st);
  if (!is_static()) {
    dynamic_header()->print(st);
  }
}

void FileMapHeader::set_as_offset(char* p, size_t *offset) {
  *offset = ArchiveBuilder::current()->any_to_offset((address)p);
}

int FileMapHeader::compute_crc() {
  char* start = (char*)this;
  // start computing from the field after _header_size to end of base archive name.
  char* buf = (char*)&(_generic_header._header_size) + sizeof(_generic_header._header_size);
  size_t sz = header_size() - (buf - start);
  int crc = ClassLoader::crc32(0, buf, (jint)sz);
  return crc;
}

// This function should only be called during run time with UseSharedSpaces enabled.
bool FileMapHeader::validate() {
  const char* file_type = CDSConfig::type_of_archive_being_loaded();
  if (_obj_alignment != ObjectAlignmentInBytes) {
    MetaspaceShared::report_loading_error("The %s's ObjectAlignmentInBytes of %d"
                                          " does not equal the current ObjectAlignmentInBytes of %d.",
                                          file_type, _obj_alignment, ObjectAlignmentInBytes);
    return false;
  }
  if (_compact_strings != CompactStrings) {
    MetaspaceShared::report_loading_error("The %s's CompactStrings setting (%s)"
                                          " does not equal the current CompactStrings setting (%s).", file_type,
                                          _compact_strings ? "enabled" : "disabled",
                                          CompactStrings   ? "enabled" : "disabled");
    return false;
  }
  bool jvmci_compiler_is_enabled = CompilerConfig::is_jvmci_compiler_enabled();
  CompilerType compiler_type = CompilerConfig::compiler_type();
  CompilerType archive_compiler_type = CompilerType(_compiler_type);
  // JVMCI compiler does different type profiling settigns and generate
  // different code. We can't use archive which was produced
  // without it and reverse.
  // Only allow mix when JIT compilation is disabled.
  // Interpreter is used by default when dumping archive.
  bool intepreter_is_used = (archive_compiler_type == CompilerType::compiler_none) ||
                            (compiler_type == CompilerType::compiler_none);
  if (!intepreter_is_used &&
      jvmci_compiler_is_enabled != (archive_compiler_type == CompilerType::compiler_jvmci)) {
    MetaspaceShared::report_loading_error("The %s's JIT compiler setting (%s)"
                                          " does not equal the current setting (%s).", file_type,
                                          compilertype2name(archive_compiler_type), compilertype2name(compiler_type));
    return false;
  }
  if (TrainingData::have_data()) {
    if (_type_profile_level != TypeProfileLevel) {
      MetaspaceShared::report_loading_error("The %s's TypeProfileLevel setting (%d)"
                                            " does not equal the current TypeProfileLevel setting (%d).", file_type,
                                            _type_profile_level, TypeProfileLevel);
      return false;
    }
    if (_type_profile_args_limit != TypeProfileArgsLimit) {
      MetaspaceShared::report_loading_error("The %s's TypeProfileArgsLimit setting (%d)"
                                            " does not equal the current TypeProfileArgsLimit setting (%d).", file_type,
                                            _type_profile_args_limit, TypeProfileArgsLimit);
      return false;
    }
    if (_type_profile_parms_limit != TypeProfileParmsLimit) {
      MetaspaceShared::report_loading_error("The %s's TypeProfileParamsLimit setting (%d)"
                                            " does not equal the current TypeProfileParamsLimit setting (%d).", file_type,
                                            _type_profile_args_limit, TypeProfileArgsLimit);
      return false;

    }
    if (_type_profile_width != TypeProfileWidth) {
      MetaspaceShared::report_loading_error("The %s's TypeProfileWidth setting (%d)"
                                            " does not equal the current TypeProfileWidth setting (%d).", file_type,
                                            (int)_type_profile_width, (int)TypeProfileWidth);
      return false;

    }
    if (_bci_profile_width != BciProfileWidth) {
      MetaspaceShared::report_loading_error("The %s's BciProfileWidth setting (%d)"
                                            " does not equal the current BciProfileWidth setting (%d).", file_type,
                                            (int)_bci_profile_width, (int)BciProfileWidth);
      return false;
    }
    if (_type_profile_casts != TypeProfileCasts) {
      MetaspaceShared::report_loading_error("The %s's TypeProfileCasts setting (%s)"
                                            " does not equal the current TypeProfileCasts setting (%s).", file_type,
                                            _type_profile_casts ? "enabled" : "disabled",
                                            TypeProfileCasts    ? "enabled" : "disabled");

      return false;

    }
    if (_profile_traps != ProfileTraps) {
      MetaspaceShared::report_loading_error("The %s's ProfileTraps setting (%s)"
                                            " does not equal the current ProfileTraps setting (%s).", file_type,
                                            _profile_traps ? "enabled" : "disabled",
                                            ProfileTraps   ? "enabled" : "disabled");

      return false;
    }
    if (_spec_trap_limit_extra_entries != SpecTrapLimitExtraEntries) {
      MetaspaceShared::report_loading_error("The %s's SpecTrapLimitExtraEntries setting (%d)"
                                            " does not equal the current SpecTrapLimitExtraEntries setting (%d).", file_type,
                                            _spec_trap_limit_extra_entries, SpecTrapLimitExtraEntries);
      return false;

    }
  }

  // This must be done after header validation because it might change the
  // header data
  const char* prop = Arguments::get_property("java.system.class.loader");
  if (prop != nullptr) {
    if (has_aot_linked_classes()) {
      MetaspaceShared::report_loading_error("%s has aot-linked classes. It cannot be used when the "
                                            "java.system.class.loader property is specified.",
                                            CDSConfig::type_of_archive_being_loaded());
      return false;
    }
    aot_log_warning(aot)("Archived non-system classes are disabled because the "
            "java.system.class.loader property is specified (value = \"%s\"). "
            "To use archived non-system classes, this property must not be set", prop);
    _has_platform_or_app_classes = false;
  }


  if (!_verify_local && BytecodeVerificationLocal) {
    //  we cannot load boot classes, so there's no point of using the CDS archive
    MetaspaceShared::report_loading_error("The %s's BytecodeVerificationLocal setting (%s)"
                                          " does not equal the current BytecodeVerificationLocal setting (%s).", file_type,
                                          _verify_local ? "enabled" : "disabled",
                                          BytecodeVerificationLocal ? "enabled" : "disabled");
    return false;
  }

  // For backwards compatibility, we don't check the BytecodeVerificationRemote setting
  // if the archive only contains system classes.
  if (_has_platform_or_app_classes
      && !_verify_remote // we didn't verify the archived platform/app classes
      && BytecodeVerificationRemote) { // but we want to verify all loaded platform/app classes
    aot_log_info(aot)("The %s was created with less restrictive "
                               "verification setting than the current setting.", file_type);
    // Pretend that we didn't have any archived platform/app classes, so they won't be loaded
    // by SystemDictionaryShared.
    _has_platform_or_app_classes = false;
  }

  // Java agents are allowed during run time. Therefore, the following condition is not
  // checked: (!_allow_archiving_with_java_agent && AllowArchivingWithJavaAgent)
  // Note: _allow_archiving_with_java_agent is set in the shared archive during dump time
  // while AllowArchivingWithJavaAgent is set during the current run.
  if (_allow_archiving_with_java_agent && !AllowArchivingWithJavaAgent) {
    MetaspaceShared::report_loading_error("The setting of the AllowArchivingWithJavaAgent is different "
                                          "from the setting in the %s.", file_type);
    return false;
  }

  if (_allow_archiving_with_java_agent) {
    aot_log_warning(aot)("This %s was created with AllowArchivingWithJavaAgent. It should be used "
            "for testing purposes only and should not be used in a production environment", file_type);
  }

  aot_log_info(aot)("The %s was created with UseCompressedOops = %d, UseCompressedClassPointers = %d, UseCompactObjectHeaders = %d",
                          file_type, compressed_oops(), compressed_class_pointers(), compact_headers());
  if (compressed_oops() != UseCompressedOops || compressed_class_pointers() != UseCompressedClassPointers) {
    aot_log_warning(aot)("Unable to use %s.\nThe saved state of UseCompressedOops and UseCompressedClassPointers is "
                               "different from runtime, CDS will be disabled.", file_type);
    return false;
  }

  if (compact_headers() != UseCompactObjectHeaders) {
    aot_log_warning(aot)("Unable to use %s.\nThe %s's UseCompactObjectHeaders setting (%s)"
                     " does not equal the current UseCompactObjectHeaders setting (%s).", file_type, file_type,
                     _compact_headers          ? "enabled" : "disabled",
                     UseCompactObjectHeaders   ? "enabled" : "disabled");
    return false;
  }

  if (!_use_optimized_module_handling && !CDSConfig::is_dumping_final_static_archive()) {
    CDSConfig::stop_using_optimized_module_handling();
    aot_log_info(aot)("optimized module handling: disabled because archive was created without optimized module handling");
  }

  if (is_static()) {
    // Only the static archive can contain the full module graph.
    if (!_has_full_module_graph) {
      CDSConfig::stop_using_full_module_graph("archive was created without full module graph");
    }
  }

  return true;
}

bool FileMapInfo::validate_header() {
  if (!header()->validate()) {
    return false;
  }
  if (_is_static) {
    return true;
  } else {
    return DynamicArchive::validate(this);
  }
}

#if INCLUDE_JVMTI
ClassPathEntry** FileMapInfo::_classpath_entries_for_jvmti = nullptr;

ClassPathEntry* FileMapInfo::get_classpath_entry_for_jvmti(int i, TRAPS) {
  if (i == 0) {
    // index 0 corresponds to the ClassPathImageEntry which is a globally shared object
    // and should never be deleted.
    return ClassLoader::get_jrt_entry();
  }
  ClassPathEntry* ent = _classpath_entries_for_jvmti[i];
  if (ent == nullptr) {
    const AOTClassLocation* cl = AOTClassLocationConfig::runtime()->class_location_at(i);
    const char* path = cl->path();
    struct stat st;
    if (os::stat(path, &st) != 0) {
      char *msg = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, strlen(path) + 128);
      jio_snprintf(msg, strlen(path) + 127, "error in finding JAR file %s", path);
      THROW_MSG_(vmSymbols::java_io_IOException(), msg, nullptr);
    } else {
      ent = ClassLoader::create_class_path_entry(THREAD, path, &st);
      if (ent == nullptr) {
        char *msg = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, char, strlen(path) + 128);
        jio_snprintf(msg, strlen(path) + 127, "error in opening JAR file %s", path);
        THROW_MSG_(vmSymbols::java_io_IOException(), msg, nullptr);
      }
    }

    MutexLocker mu(THREAD, CDSClassFileStream_lock);
    if (_classpath_entries_for_jvmti[i] == nullptr) {
      _classpath_entries_for_jvmti[i] = ent;
    } else {
      // Another thread has beat me to creating this entry
      delete ent;
      ent = _classpath_entries_for_jvmti[i];
    }
  }

  return ent;
}

ClassFileStream* FileMapInfo::open_stream_for_jvmti(InstanceKlass* ik, Handle class_loader, TRAPS) {
  int path_index = ik->shared_classpath_index();
  assert(path_index >= 0, "should be called for shared built-in classes only");
  assert(path_index < AOTClassLocationConfig::runtime()->length(), "sanity");

  ClassPathEntry* cpe = get_classpath_entry_for_jvmti(path_index, CHECK_NULL);
  assert(cpe != nullptr, "must be");

  Symbol* name = ik->name();
  const char* const class_name = name->as_C_string();
  const char* const file_name = ClassLoader::file_name_for_class_name(class_name,
                                                                      name->utf8_length());
  ClassLoaderData* loader_data = ClassLoaderData::class_loader_data(class_loader());
  const AOTClassLocation* cl = AOTClassLocationConfig::runtime()->class_location_at(path_index);
  ClassFileStream* cfs;
  if (class_loader() != nullptr && cl->is_multi_release_jar()) {
    // This class was loaded from a multi-release JAR file during dump time. The
    // process for finding its classfile is complex. Let's defer to the Java code
    // in java.lang.ClassLoader.
    cfs = get_stream_from_class_loader(class_loader, cpe, file_name, CHECK_NULL);
  } else {
    cfs = cpe->open_stream_for_loader(THREAD, file_name, loader_data);
  }
  assert(cfs != nullptr, "must be able to read the classfile data of shared classes for built-in loaders.");
  log_debug(aot, jvmti)("classfile data for %s [%d: %s] = %d bytes", class_name, path_index,
                        cfs->source(), cfs->length());
  return cfs;
}

ClassFileStream* FileMapInfo::get_stream_from_class_loader(Handle class_loader,
                                                           ClassPathEntry* cpe,
                                                           const char* file_name,
                                                           TRAPS) {
  JavaValue result(T_OBJECT);
  oop class_name = java_lang_String::create_oop_from_str(file_name, THREAD);
  Handle h_class_name = Handle(THREAD, class_name);

  // byte[] ClassLoader.getResourceAsByteArray(String name)
  JavaCalls::call_virtual(&result,
                          class_loader,
                          vmClasses::ClassLoader_klass(),
                          vmSymbols::getResourceAsByteArray_name(),
                          vmSymbols::getResourceAsByteArray_signature(),
                          h_class_name,
                          CHECK_NULL);
  assert(result.get_type() == T_OBJECT, "just checking");
  oop obj = result.get_oop();
  assert(obj != nullptr, "ClassLoader.getResourceAsByteArray should not return null");

  // copy from byte[] to a buffer
  typeArrayOop ba = typeArrayOop(obj);
  jint len = ba->length();
  u1* buffer = NEW_RESOURCE_ARRAY(u1, len);
  ArrayAccess<>::arraycopy_to_native<>(ba, typeArrayOopDesc::element_offset<jbyte>(0), buffer, len);

  return new ClassFileStream(buffer, len, cpe->name());
}
#endif
