/*
 * Copyright (c) 2003, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoader.hpp"
#include "classfile/compactHashtable.inline.hpp"
#include "classfile/sharedClassUtil.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/altHashing.hpp"
#if INCLUDE_ALL_GCS
#include "gc/g1/g1CollectedHeap.hpp"
#endif
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "logging/logMessage.hpp"
#include "memory/filemap.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/oopFactory.hpp"
#include "oops/objArrayOop.hpp"
#include "prims/jvm.h"
#include "prims/jvmtiExport.hpp"
#include "runtime/arguments.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "runtime/vm_version.hpp"
#include "services/memTracker.hpp"
#include "utilities/align.hpp"
#include "utilities/defaultStream.hpp"

# include <sys/stat.h>
# include <errno.h>

#ifndef O_BINARY       // if defined (Win32) use binary files.
#define O_BINARY 0     // otherwise do nothing.
#endif

extern address JVM_FunctionAtStart();
extern address JVM_FunctionAtEnd();

// Complain and stop. All error conditions occurring during the writing of
// an archive file should stop the process.  Unrecoverable errors during
// the reading of the archive file should stop the process.

static void fail(const char *msg, va_list ap) {
  // This occurs very early during initialization: tty is not initialized.
  jio_fprintf(defaultStream::error_stream(),
              "An error has occurred while processing the"
              " shared archive file.\n");
  jio_vfprintf(defaultStream::error_stream(), msg, ap);
  jio_fprintf(defaultStream::error_stream(), "\n");
  // Do not change the text of the below message because some tests check for it.
  vm_exit_during_initialization("Unable to use shared archive.", NULL);
}


void FileMapInfo::fail_stop(const char *msg, ...) {
        va_list ap;
  va_start(ap, msg);
  fail(msg, ap);        // Never returns.
  va_end(ap);           // for completeness.
}


// Complain and continue.  Recoverable errors during the reading of the
// archive file may continue (with sharing disabled).
//
// If we continue, then disable shared spaces and close the file.

void FileMapInfo::fail_continue(const char *msg, ...) {
  va_list ap;
  va_start(ap, msg);
  MetaspaceShared::set_archive_loading_failed();
  if (PrintSharedArchiveAndExit && _validating_classpath_entry_table) {
    // If we are doing PrintSharedArchiveAndExit and some of the classpath entries
    // do not validate, we can still continue "limping" to validate the remaining
    // entries. No need to quit.
    tty->print("[");
    tty->vprint(msg, ap);
    tty->print_cr("]");
  } else {
    if (RequireSharedSpaces) {
      fail(msg, ap);
    } else {
      if (log_is_enabled(Info, cds)) {
        ResourceMark rm;
        LogStream ls(Log(cds)::info());
        ls.print("UseSharedSpaces: ");
        ls.vprint_cr(msg, ap);
      }
    }
    UseSharedSpaces = false;
    assert(current_info() != NULL, "singleton must be registered");
    current_info()->close();
  }
  va_end(ap);
}

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

  if (version_len < (JVM_IDENT_MAX-1)) {
    strcpy(header_version, vm_version);

  } else {
    // Get the hash value.  Use a static seed because the hash needs to return the same
    // value over multiple jvm invocations.
    unsigned int hash = AltHashing::murmur3_32(8191, (const jbyte*)vm_version, version_len);

    // Truncate the ident, saving room for the 8 hex character hash value.
    strncpy(header_version, vm_version, JVM_IDENT_MAX-9);

    // Append the hash code as eight hex digits.
    sprintf(&header_version[JVM_IDENT_MAX-9], "%08x", hash);
    header_version[JVM_IDENT_MAX-1] = 0;  // Null terminate.
  }
}

FileMapInfo::FileMapInfo() {
  assert(_current_info == NULL, "must be singleton"); // not thread safe
  _current_info = this;
  memset((void*)this, 0, sizeof(FileMapInfo));
  _file_offset = 0;
  _file_open = false;
  _header = SharedClassUtil::allocate_file_map_header();
  _header->_version = _invalid_version;
}

FileMapInfo::~FileMapInfo() {
  assert(_current_info == this, "must be singleton"); // not thread safe
  _current_info = NULL;
}

void FileMapInfo::populate_header(size_t alignment) {
  _header->populate(this, alignment);
}

size_t FileMapInfo::FileMapHeader::data_size() {
  return SharedClassUtil::file_map_header_size() - sizeof(FileMapInfo::FileMapHeaderBase);
}

void FileMapInfo::FileMapHeader::populate(FileMapInfo* mapinfo, size_t alignment) {
  _magic = 0xf00baba2;
  _version = _current_version;
  _alignment = alignment;
  _obj_alignment = ObjectAlignmentInBytes;
  _compact_strings = CompactStrings;
  _narrow_oop_mode = Universe::narrow_oop_mode();
  _narrow_oop_shift = Universe::narrow_oop_shift();
  _max_heap_size = MaxHeapSize;
  _narrow_klass_base = Universe::narrow_klass_base();
  _narrow_klass_shift = Universe::narrow_klass_shift();
  _classpath_entry_table_size = mapinfo->_classpath_entry_table_size;
  _classpath_entry_table = mapinfo->_classpath_entry_table;
  _classpath_entry_size = mapinfo->_classpath_entry_size;

  // The following fields are for sanity checks for whether this archive
  // will function correctly with this JVM and the bootclasspath it's
  // invoked with.

  // JVM version string ... changes on each build.
  get_header_version(_jvm_ident);
}

void SharedClassPathEntry::init(const char* name, TRAPS) {
  _timestamp = 0;
  _filesize  = 0;

  struct stat st;
  if (os::stat(name, &st) == 0) {
    if ((st.st_mode & S_IFMT) == S_IFDIR) {
      if (!os::dir_is_empty(name)) {
        ClassLoader::exit_with_path_failure(
                  "Cannot have non-empty directory in archived classpaths", name);
      }
      _is_dir = true;
    } else {
      _is_dir = false;
      _timestamp = st.st_mtime;
      _filesize = st.st_size;
    }
  } else {
    // The file/dir must exist, or it would not have been added
    // into ClassLoader::classpath_entry().
    //
    // If we can't access a jar file in the boot path, then we can't
    // make assumptions about where classes get loaded from.
    FileMapInfo::fail_stop("Unable to open file %s.", name);
  }

  size_t len = strlen(name) + 1;
  _name = MetadataFactory::new_array<char>(ClassLoaderData::the_null_class_loader_data(), (int)len, THREAD);
  strcpy(_name->data(), name);
}

bool SharedClassPathEntry::validate() {
  struct stat st;
  const char* name = this->name();
  bool ok = true;
  log_info(class, path)("checking shared classpath entry: %s", name);
  if (os::stat(name, &st) != 0) {
    FileMapInfo::fail_continue("Required classpath entry does not exist: %s", name);
    ok = false;
  } else if (is_dir()) {
    if (!os::dir_is_empty(name)) {
      FileMapInfo::fail_continue("directory is not empty: %s", name);
      ok = false;
    }
  } else if (is_jar_or_bootimage()) {
    if (_timestamp != st.st_mtime ||
        _filesize != st.st_size) {
      ok = false;
      if (PrintSharedArchiveAndExit) {
        FileMapInfo::fail_continue(_timestamp != st.st_mtime ?
                                   "Timestamp mismatch" :
                                   "File size mismatch");
      } else {
        FileMapInfo::fail_continue("A jar/jimage file is not the one used while building"
                                   " the shared archive file: %s", name);
      }
    }
  }
  return ok;
}

void SharedClassPathEntry::metaspace_pointers_do(MetaspaceClosure* it) {
  it->push(&_name);
  it->push(&_manifest);
}

void FileMapInfo::allocate_classpath_entry_table() {
  Thread* THREAD = Thread::current();
  ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
  size_t entry_size = SharedClassUtil::shared_class_path_entry_size(); // assert ( should be 8 byte aligned??)
  int num_entries = ClassLoader::number_of_classpath_entries();
  size_t bytes = entry_size * num_entries;

  _classpath_entry_table = MetadataFactory::new_array<u8>(loader_data, (int)(bytes + 7 / 8), THREAD);
  _classpath_entry_table_size = num_entries;
  _classpath_entry_size = entry_size;

  assert(ClassLoader::get_jrt_entry() != NULL,
         "No modular java runtime image present when allocating the CDS classpath entry table");

  for (int i=0; i<num_entries; i++) {
    ClassPathEntry *cpe = ClassLoader::classpath_entry(i);
    const char* type = ((i == 0) ? "jrt" : (cpe->is_jar_file() ? "jar" : "dir"));

    log_info(class, path)("add main shared path (%s) %s", type, cpe->name());
    SharedClassPathEntry* ent = shared_classpath(i);
    ent->init(cpe->name(), THREAD);

    if (i > 0) { // No need to do jimage.
      EXCEPTION_MARK; // The following call should never throw, but would exit VM on error.
      SharedClassUtil::update_shared_classpath(cpe, ent, THREAD);
    }
  }
}

bool FileMapInfo::validate_classpath_entry_table() {
  _validating_classpath_entry_table = true;

  int count = _header->_classpath_entry_table_size;

  _classpath_entry_table = _header->_classpath_entry_table;
  _classpath_entry_size = _header->_classpath_entry_size;
  _classpath_entry_table_size = _header->_classpath_entry_table_size;

  for (int i=0; i<count; i++) {
    if (shared_classpath(i)->validate()) {
      log_info(class, path)("ok");
    } else if (!PrintSharedArchiveAndExit) {
      _validating_classpath_entry_table = false;
      _classpath_entry_table = NULL;
      _classpath_entry_table_size = 0;
      return false;
    }
  }

  _validating_classpath_entry_table = false;
  return true;
}


// Read the FileMapInfo information from the file.

bool FileMapInfo::init_from_file(int fd) {
  size_t sz = _header->data_size();
  char* addr = _header->data();
  size_t n = os::read(fd, addr, (unsigned int)sz);
  if (n != sz) {
    fail_continue("Unable to read the file header.");
    return false;
  }
  if (_header->_version != current_version()) {
    fail_continue("The shared archive file has the wrong version.");
    return false;
  }
  _file_offset = (long)n;

  size_t info_size = _header->_paths_misc_info_size;
  _paths_misc_info = NEW_C_HEAP_ARRAY_RETURN_NULL(char, info_size, mtClass);
  if (_paths_misc_info == NULL) {
    fail_continue("Unable to read the file header.");
    return false;
  }
  n = os::read(fd, _paths_misc_info, (unsigned int)info_size);
  if (n != info_size) {
    fail_continue("Unable to read the shared path info header.");
    FREE_C_HEAP_ARRAY(char, _paths_misc_info);
    _paths_misc_info = NULL;
    return false;
  }

  size_t len = lseek(fd, 0, SEEK_END);
  struct FileMapInfo::FileMapHeader::space_info* si =
    &_header->_space[MetaspaceShared::last_valid_region];
  if (si->_file_offset >= len || len - si->_file_offset < si->_used) {
    fail_continue("The shared archive file has been truncated.");
    return false;
  }

  _file_offset += (long)n;
  return true;
}


// Read the FileMapInfo information from the file.
bool FileMapInfo::open_for_read() {
  _full_path = Arguments::GetSharedArchivePath();
  int fd = open(_full_path, O_RDONLY | O_BINARY, 0);
  if (fd < 0) {
    if (errno == ENOENT) {
      // Not locating the shared archive is ok.
      fail_continue("Specified shared archive not found.");
    } else {
      fail_continue("Failed to open shared archive file (%s).",
                    os::strerror(errno));
    }
    return false;
  }

  _fd = fd;
  _file_open = true;
  return true;
}


// Write the FileMapInfo information to the file.

void FileMapInfo::open_for_write() {
 _full_path = Arguments::GetSharedArchivePath();
  if (log_is_enabled(Info, cds)) {
    ResourceMark rm;
    LogMessage(cds) msg;
    stringStream info_stream;
    info_stream.print_cr("Dumping shared data to file: ");
    info_stream.print_cr("   %s", _full_path);
    msg.info("%s", info_stream.as_string());
  }

#ifdef _WINDOWS  // On Windows, need WRITE permission to remove the file.
  chmod(_full_path, _S_IREAD | _S_IWRITE);
#endif

  // Use remove() to delete the existing file because, on Unix, this will
  // allow processes that have it open continued access to the file.
  remove(_full_path);
  int fd = open(_full_path, O_RDWR | O_CREAT | O_TRUNC | O_BINARY, 0444);
  if (fd < 0) {
    fail_stop("Unable to create shared archive file %s: (%s).", _full_path,
              os::strerror(errno));
  }
  _fd = fd;
  _file_offset = 0;
  _file_open = true;
}


// Write the header to the file, seek to the next allocation boundary.

void FileMapInfo::write_header() {
  int info_size = ClassLoader::get_shared_paths_misc_info_size();

  _header->_paths_misc_info_size = info_size;

  align_file_position();
  size_t sz = _header->data_size();
  char* addr = _header->data();
  write_bytes(addr, (int)sz); // skip the C++ vtable
  write_bytes(ClassLoader::get_shared_paths_misc_info(), info_size);
  align_file_position();
}


// Dump region to file.

void FileMapInfo::write_region(int region, char* base, size_t size,
                               bool read_only, bool allow_exec) {
  struct FileMapInfo::FileMapHeader::space_info* si = &_header->_space[region];

  if (_file_open) {
    guarantee(si->_file_offset == _file_offset, "file offset mismatch.");
    log_info(cds)("Shared file region %d: " SIZE_FORMAT_HEX_W(08)
                  " bytes, addr " INTPTR_FORMAT " file offset " SIZE_FORMAT_HEX_W(08),
                  region, size, p2i(base), _file_offset);
  } else {
    si->_file_offset = _file_offset;
  }
  if (MetaspaceShared::is_string_region(region)) {
    assert((base - (char*)Universe::narrow_oop_base()) % HeapWordSize == 0, "Sanity");
    if (base != NULL) {
      si->_addr._offset = (intx)oopDesc::encode_heap_oop_not_null((oop)base);
    } else {
      si->_addr._offset = 0;
    }
  } else {
    si->_addr._base = base;
  }
  si->_used = size;
  si->_read_only = read_only;
  si->_allow_exec = allow_exec;
  si->_crc = ClassLoader::crc32(0, base, (jint)size);
  write_bytes_aligned(base, (int)size);
}

// Write the string space. The string space contains one or multiple GC(G1) regions.
// When the total string space size is smaller than one GC region of the dump time,
// only one string region is used for shared strings.
//
// If the total string space size is bigger than one GC region, there would be more
// than one GC regions allocated for shared strings. The first/bottom GC region might
// be a partial GC region with the empty portion at the higher address within that region.
// The non-empty portion of the first region is written into the archive as one string
// region. The rest are consecutive full GC regions if they exist, which can be written
// out in one chunk as another string region.
//
// Here's the mapping from (GrowableArray<MemRegion> *regions) -> (metaspace string regions).
//   + We have 1 or more heap regions: r0, r1, r2 ..... rn
//   + We have 2 metaspace string regions: s0 and s1
//
// If there's a single heap region (r0), then s0 == r0, and s1 is empty.
// Otherwise:
//
// "X" represented space that's occupied by heap objects.
// "_" represented unused spaced in the heap region.
//
//
//    |r0        | r1  | r2 | ...... | rn |
//    |XXXXXX|__ |XXXXX|XXXX|XXXXXXXX|XXXX|
//    |<-s0->|   |<- s1 ----------------->|
//            ^^^
//             |
//             +-- unmapped space
void FileMapInfo::write_string_regions(GrowableArray<MemRegion> *regions,
                                       char** st0_start, char** st0_top, char** st0_end,
                                       char** st1_start, char** st1_top, char** st1_end) {
  *st0_start = *st0_top = *st0_end = NULL;
  *st1_start = *st1_top = *st1_end = NULL;

  assert(MetaspaceShared::max_strings == 2, "this loop doesn't work for any other value");
  for (int i = MetaspaceShared::first_string;
           i < MetaspaceShared::first_string + MetaspaceShared::max_strings; i++) {
    char* start = NULL;
    size_t size = 0;
    int len = regions->length();
    if (len > 0) {
      if (i == MetaspaceShared::first_string) {
        MemRegion first = regions->first();
        start = (char*)first.start();
        size = first.byte_size();
        *st0_start = start;
        *st0_top = start + size;
        if (len > 1) {
          *st0_end = (char*)regions->at(1).start();
        } else {
          *st0_end = start + size;
        }
      } else {
        assert(i == MetaspaceShared::first_string + 1, "must be");
        if (len > 1) {
          start = (char*)regions->at(1).start();
          size = (char*)regions->at(len - 1).end() - start;
          *st1_start = start;
          *st1_top = start + size;
          *st1_end = start + size;
        }
      }
    }
    log_info(cds)("String region %d " INTPTR_FORMAT " - " INTPTR_FORMAT " = " SIZE_FORMAT_W(8) " bytes",
                  i, p2i(start), p2i(start + size), size);
    write_region(i, start, size, false, false);
  }
}


// Dump bytes to file -- at the current file position.

void FileMapInfo::write_bytes(const void* buffer, int nbytes) {
  if (_file_open) {
    int n = ::write(_fd, buffer, nbytes);
    if (n != nbytes) {
      // It is dangerous to leave the corrupted shared archive file around,
      // close and remove the file. See bug 6372906.
      close();
      remove(_full_path);
      fail_stop("Unable to write to shared archive file.");
    }
  }
  _file_offset += nbytes;
}


// Align file position to an allocation unit boundary.

void FileMapInfo::align_file_position() {
  size_t new_file_offset = align_up(_file_offset,
                                         os::vm_allocation_granularity());
  if (new_file_offset != _file_offset) {
    _file_offset = new_file_offset;
    if (_file_open) {
      // Seek one byte back from the target and write a byte to insure
      // that the written file is the correct length.
      _file_offset -= 1;
      if (lseek(_fd, (long)_file_offset, SEEK_SET) < 0) {
        fail_stop("Unable to seek.");
      }
      char zero = 0;
      write_bytes(&zero, 1);
    }
  }
}


// Dump bytes to file -- at the current file position.

void FileMapInfo::write_bytes_aligned(const void* buffer, int nbytes) {
  align_file_position();
  write_bytes(buffer, nbytes);
  align_file_position();
}


// Close the shared archive file.  This does NOT unmap mapped regions.

void FileMapInfo::close() {
  if (_file_open) {
    if (::close(_fd) < 0) {
      fail_stop("Unable to close the shared archive file.");
    }
    _file_open = false;
    _fd = -1;
  }
}


// JVM/TI RedefineClasses() support:
// Remap the shared readonly space to shared readwrite, private.
bool FileMapInfo::remap_shared_readonly_as_readwrite() {
  int idx = MetaspaceShared::ro;
  struct FileMapInfo::FileMapHeader::space_info* si = &_header->_space[idx];
  if (!si->_read_only) {
    // the space is already readwrite so we are done
    return true;
  }
  size_t used = si->_used;
  size_t size = align_up(used, os::vm_allocation_granularity());
  if (!open_for_read()) {
    return false;
  }
  char *addr = _header->region_addr(idx);
  char *base = os::remap_memory(_fd, _full_path, si->_file_offset,
                                addr, size, false /* !read_only */,
                                si->_allow_exec);
  close();
  if (base == NULL) {
    fail_continue("Unable to remap shared readonly space (errno=%d).", errno);
    return false;
  }
  if (base != addr) {
    fail_continue("Unable to remap shared readonly space at required address.");
    return false;
  }
  si->_read_only = false;
  return true;
}

// Map the whole region at once, assumed to be allocated contiguously.
ReservedSpace FileMapInfo::reserve_shared_memory() {
  char* requested_addr = _header->region_addr(0);
  size_t size = FileMapInfo::core_spaces_size();

  // Reserve the space first, then map otherwise map will go right over some
  // other reserved memory (like the code cache).
  ReservedSpace rs(size, os::vm_allocation_granularity(), false, requested_addr);
  if (!rs.is_reserved()) {
    fail_continue("Unable to reserve shared space at required address "
                  INTPTR_FORMAT, p2i(requested_addr));
    return rs;
  }
  // the reserved virtual memory is for mapping class data sharing archive
  MemTracker::record_virtual_memory_type((address)rs.base(), mtClassShared);

  return rs;
}

// Memory map a region in the address space.
static const char* shared_region_name[] = { "ReadOnly", "ReadWrite", "MiscData", "MiscCode",
                                            "String1", "String2", "OptionalData" };

char* FileMapInfo::map_region(int i) {
  assert(!MetaspaceShared::is_string_region(i), "sanity");
  struct FileMapInfo::FileMapHeader::space_info* si = &_header->_space[i];
  size_t used = si->_used;
  size_t alignment = os::vm_allocation_granularity();
  size_t size = align_up(used, alignment);
  char *requested_addr = _header->region_addr(i);

  // If a tool agent is in use (debugging enabled), we must map the address space RW
  if (JvmtiExport::can_modify_any_class() || JvmtiExport::can_walk_any_space()) {
    si->_read_only = false;
  }

  // map the contents of the CDS archive in this memory
  char *base = os::map_memory(_fd, _full_path, si->_file_offset,
                              requested_addr, size, si->_read_only,
                              si->_allow_exec);
  if (base == NULL || base != requested_addr) {
    fail_continue("Unable to map %s shared space at required address.", shared_region_name[i]);
    return NULL;
  }
#ifdef _WINDOWS
  // This call is Windows-only because the memory_type gets recorded for the other platforms
  // in method FileMapInfo::reserve_shared_memory(), which is not called on Windows.
  MemTracker::record_virtual_memory_type((address)base, mtClassShared);
#endif

  return base;
}

static MemRegion *string_ranges = NULL;
static int num_ranges = 0;
bool FileMapInfo::map_string_regions() {
#if INCLUDE_ALL_GCS
  if (UseG1GC && UseCompressedOops && UseCompressedClassPointers) {
    // Check that all the narrow oop and klass encodings match the archive
    if (narrow_oop_mode() != Universe::narrow_oop_mode() ||
        narrow_oop_shift() != Universe::narrow_oop_shift() ||
        narrow_klass_base() != Universe::narrow_klass_base() ||
        narrow_klass_shift() != Universe::narrow_klass_shift()) {
      if (log_is_enabled(Info, cds) && _header->_space[MetaspaceShared::first_string]._used > 0) {
        log_info(cds)("Shared string data from the CDS archive is being ignored. "
                      "The current CompressedOops/CompressedClassPointers encoding differs from "
                      "that archived due to heap size change. The archive was dumped using max heap "
                      "size " UINTX_FORMAT "M.", max_heap_size()/M);
      }
    } else {
      string_ranges = new MemRegion[MetaspaceShared::max_strings];
      struct FileMapInfo::FileMapHeader::space_info* si;

      for (int i = MetaspaceShared::first_string;
               i < MetaspaceShared::first_string + MetaspaceShared::max_strings; i++) {
        si = &_header->_space[i];
        size_t used = si->_used;
        if (used > 0) {
          size_t size = used;
          char* requested_addr = (char*)((void*)oopDesc::decode_heap_oop_not_null(
                                                 (narrowOop)si->_addr._offset));
          string_ranges[num_ranges] = MemRegion((HeapWord*)requested_addr, size / HeapWordSize);
          num_ranges ++;
        }
      }

      if (num_ranges == 0) {
        StringTable::ignore_shared_strings(true);
        return true; // no shared string data
      }

      // Check that ranges are within the java heap
      if (!G1CollectedHeap::heap()->check_archive_addresses(string_ranges, num_ranges)) {
        fail_continue("Unable to allocate shared string space: range is not "
                      "within java heap.");
        return false;
      }

      // allocate from java heap
      if (!G1CollectedHeap::heap()->alloc_archive_regions(string_ranges, num_ranges)) {
        fail_continue("Unable to allocate shared string space: range is "
                      "already in use.");
        return false;
      }

      // Map the string data. No need to call MemTracker::record_virtual_memory_type()
      // for mapped string regions as they are part of the reserved java heap, which
      // is already recorded.
      for (int i = 0; i < num_ranges; i++) {
        si = &_header->_space[MetaspaceShared::first_string + i];
        char* addr = (char*)string_ranges[i].start();
        char* base = os::map_memory(_fd, _full_path, si->_file_offset,
                                    addr, string_ranges[i].byte_size(), si->_read_only,
                                    si->_allow_exec);
        if (base == NULL || base != addr) {
          // dealloc the string regions from java heap
          dealloc_string_regions();
          fail_continue("Unable to map shared string space at required address.");
          return false;
        }
      }

      if (!verify_string_regions()) {
        // dealloc the string regions from java heap
        dealloc_string_regions();
        fail_continue("Shared string regions are corrupt");
        return false;
      }

      // the shared string data is mapped successfully
      return true;
    }
  } else {
    if (log_is_enabled(Info, cds) && _header->_space[MetaspaceShared::first_string]._used > 0) {
      log_info(cds)("Shared string data from the CDS archive is being ignored. UseG1GC, "
                    "UseCompressedOops and UseCompressedClassPointers are required.");
    }
  }

  // if we get here, the shared string data is not mapped
  assert(string_ranges == NULL && num_ranges == 0, "sanity");
  StringTable::ignore_shared_strings(true);
#endif
  return true;
}

bool FileMapInfo::verify_string_regions() {
  for (int i = MetaspaceShared::first_string;
           i < MetaspaceShared::first_string + MetaspaceShared::max_strings; i++) {
    if (!verify_region_checksum(i)) {
      return false;
    }
  }
  return true;
}

void FileMapInfo::fixup_string_regions() {
#if INCLUDE_ALL_GCS
  // If any string regions were found, call the fill routine to make them parseable.
  // Note that string_ranges may be non-NULL even if no ranges were found.
  if (num_ranges != 0) {
    assert(string_ranges != NULL, "Null string_ranges array with non-zero count");
    G1CollectedHeap::heap()->fill_archive_regions(string_ranges, num_ranges);
  }
#endif
}

bool FileMapInfo::verify_region_checksum(int i) {
  if (!VerifySharedSpaces) {
    return true;
  }

  size_t sz = _header->_space[i]._used;

  if (sz == 0) {
    return true; // no data
  }
  if (MetaspaceShared::is_string_region(i) && StringTable::shared_string_ignored()) {
    return true; // shared string data are not mapped
  }
  const char* buf = _header->region_addr(i);
  int crc = ClassLoader::crc32(0, buf, (jint)sz);
  if (crc != _header->_space[i]._crc) {
    fail_continue("Checksum verification failed.");
    return false;
  }
  return true;
}

// Unmap a memory region in the address space.

void FileMapInfo::unmap_region(int i) {
  assert(!MetaspaceShared::is_string_region(i), "sanity");
  struct FileMapInfo::FileMapHeader::space_info* si = &_header->_space[i];
  size_t used = si->_used;
  size_t size = align_up(used, os::vm_allocation_granularity());

  if (used == 0) {
    return;
  }

  char* addr = _header->region_addr(i);
  if (!os::unmap_memory(addr, size)) {
    fail_stop("Unable to unmap shared space.");
  }
}

// dealloc the archived string region from java heap
void FileMapInfo::dealloc_string_regions() {
#if INCLUDE_ALL_GCS
  if (num_ranges > 0) {
    assert(string_ranges != NULL, "Null string_ranges array with non-zero count");
    G1CollectedHeap::heap()->dealloc_archive_regions(string_ranges, num_ranges);
  }
#endif
}

void FileMapInfo::assert_mark(bool check) {
  if (!check) {
    fail_stop("Mark mismatch while restoring from shared file.");
  }
}

void FileMapInfo::metaspace_pointers_do(MetaspaceClosure* it) {
  it->push(&_classpath_entry_table);
  for (int i=0; i<_classpath_entry_table_size; i++) {
    shared_classpath(i)->metaspace_pointers_do(it);
  }
}


FileMapInfo* FileMapInfo::_current_info = NULL;
Array<u8>* FileMapInfo::_classpath_entry_table = NULL;
int FileMapInfo::_classpath_entry_table_size = 0;
size_t FileMapInfo::_classpath_entry_size = 0x1234baad;
bool FileMapInfo::_validating_classpath_entry_table = false;

// Open the shared archive file, read and validate the header
// information (version, boot classpath, etc.).  If initialization
// fails, shared spaces are disabled and the file is closed. [See
// fail_continue.]
//
// Validation of the archive is done in two steps:
//
// [1] validate_header() - done here. This checks the header, including _paths_misc_info.
// [2] validate_classpath_entry_table - this is done later, because the table is in the RW
//     region of the archive, which is not mapped yet.
bool FileMapInfo::initialize() {
  assert(UseSharedSpaces, "UseSharedSpaces expected.");

  if (!open_for_read()) {
    return false;
  }

  init_from_file(_fd);
  if (!validate_header()) {
    return false;
  }
  return true;
}

char* FileMapInfo::FileMapHeader::region_addr(int idx) {
  if (MetaspaceShared::is_string_region(idx)) {
    return (char*)((void*)oopDesc::decode_heap_oop_not_null(
              (narrowOop)_space[idx]._addr._offset));
  } else {
    return _space[idx]._addr._base;
  }
}

int FileMapInfo::FileMapHeader::compute_crc() {
  char* header = data();
  // start computing from the field after _crc
  char* buf = (char*)&_crc + sizeof(int);
  size_t sz = data_size() - (buf - header);
  int crc = ClassLoader::crc32(0, buf, (jint)sz);
  return crc;
}

bool FileMapInfo::FileMapHeader::validate() {
  if (VerifySharedSpaces && compute_crc() != _crc) {
    fail_continue("Header checksum verification failed.");
    return false;
  }

  if (!Arguments::has_jimage()) {
    FileMapInfo::fail_continue("The shared archive file cannot be used with an exploded module build.");
    return false;
  }

  if (_version != current_version()) {
    FileMapInfo::fail_continue("The shared archive file is the wrong version.");
    return false;
  }
  if (_magic != (int)0xf00baba2) {
    FileMapInfo::fail_continue("The shared archive file has a bad magic number.");
    return false;
  }
  char header_version[JVM_IDENT_MAX];
  get_header_version(header_version);
  if (strncmp(_jvm_ident, header_version, JVM_IDENT_MAX-1) != 0) {
    log_info(class, path)("expected: %s", header_version);
    log_info(class, path)("actual:   %s", _jvm_ident);
    FileMapInfo::fail_continue("The shared archive file was created by a different"
                  " version or build of HotSpot");
    return false;
  }
  if (_obj_alignment != ObjectAlignmentInBytes) {
    FileMapInfo::fail_continue("The shared archive file's ObjectAlignmentInBytes of %d"
                  " does not equal the current ObjectAlignmentInBytes of " INTX_FORMAT ".",
                  _obj_alignment, ObjectAlignmentInBytes);
    return false;
  }
  if (_compact_strings != CompactStrings) {
    FileMapInfo::fail_continue("The shared archive file's CompactStrings setting (%s)"
                  " does not equal the current CompactStrings setting (%s).",
                  _compact_strings ? "enabled" : "disabled",
                  CompactStrings   ? "enabled" : "disabled");
    return false;
  }

  return true;
}

bool FileMapInfo::validate_header() {
  bool status = _header->validate();

  if (status) {
    if (!ClassLoader::check_shared_paths_misc_info(_paths_misc_info, _header->_paths_misc_info_size)) {
      if (!PrintSharedArchiveAndExit) {
        fail_continue("shared class paths mismatch (hint: enable -Xlog:class+path=info to diagnose the failure)");
        status = false;
      }
    }
  }

  if (_paths_misc_info != NULL) {
    FREE_C_HEAP_ARRAY(char, _paths_misc_info);
    _paths_misc_info = NULL;
  }
  return status;
}

// The following method is provided to see whether a given pointer
// falls in the mapped shared space.
// Param:
// p, The given pointer
// Return:
// True if the p is within the mapped shared space, otherwise, false.
bool FileMapInfo::is_in_shared_space(const void* p) {
  for (int i = 0; i < MetaspaceShared::n_regions; i++) {
    char *base;
    if (MetaspaceShared::is_string_region(i) && _header->_space[i]._used == 0) {
      continue;
    }
    base = _header->region_addr(i);
    if (p >= base && p < base + _header->_space[i]._used) {
      return true;
    }
  }

  return false;
}

// Check if a given address is within one of the shared regions ( ro, rw, mc or md)
bool FileMapInfo::is_in_shared_region(const void* p, int idx) {
  assert(idx == MetaspaceShared::ro ||
         idx == MetaspaceShared::rw ||
         idx == MetaspaceShared::mc ||
         idx == MetaspaceShared::md, "invalid region index");
  char* base = _header->region_addr(idx);
  if (p >= base && p < base + _header->_space[idx]._used) {
    return true;
  }
  return false;
}

void FileMapInfo::print_shared_spaces() {
  tty->print_cr("Shared Spaces:");
  for (int i = 0; i < MetaspaceShared::n_regions; i++) {
    struct FileMapInfo::FileMapHeader::space_info* si = &_header->_space[i];
    char *base = _header->region_addr(i);
    tty->print("  %s " INTPTR_FORMAT "-" INTPTR_FORMAT,
                        shared_region_name[i],
                        p2i(base), p2i(base + si->_used));
  }
}

// Unmap mapped regions of shared space.
void FileMapInfo::stop_sharing_and_unmap(const char* msg) {
  FileMapInfo *map_info = FileMapInfo::current_info();
  if (map_info) {
    map_info->fail_continue("%s", msg);
    for (int i = 0; i < MetaspaceShared::num_non_strings; i++) {
      char *addr = map_info->_header->region_addr(i);
      if (addr != NULL && !MetaspaceShared::is_string_region(i)) {
        map_info->unmap_region(i);
        map_info->_header->_space[i]._addr._base = NULL;
      }
    }
    // Dealloc the string regions only without unmapping. The string regions are part
    // of the java heap. Unmapping of the heap regions are managed by GC.
    map_info->dealloc_string_regions();
  } else if (DumpSharedSpaces) {
    fail_stop("%s", msg);
  }
}
