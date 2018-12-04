/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoader.inline.hpp"
#include "classfile/classLoaderExt.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionaryShared.hpp"
#include "classfile/altHashing.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "logging/logMessage.hpp"
#include "memory/filemap.hpp"
#include "memory/heapShared.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "memory/metaspaceShared.hpp"
#include "memory/oopFactory.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/arguments.hpp"
#include "runtime/java.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/vm_version.hpp"
#include "services/memTracker.hpp"
#include "utilities/align.hpp"
#include "utilities/defaultStream.hpp"
#if INCLUDE_G1GC
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/heapRegion.hpp"
#endif

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
  if (PrintSharedArchiveAndExit && _validating_shared_path_table) {
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
  _header = (FileMapHeader*)os::malloc(sizeof(FileMapHeader), mtInternal);
  _header->_version = INVALID_CDS_ARCHIVE_VERSION;
  _header->_has_platform_or_app_classes = true;
}

FileMapInfo::~FileMapInfo() {
  assert(_current_info == this, "must be singleton"); // not thread safe
  _current_info = NULL;
}

void FileMapInfo::populate_header(size_t alignment) {
  _header->populate(this, alignment);
}

void FileMapHeader::populate(FileMapInfo* mapinfo, size_t alignment) {
  _magic = CDS_ARCHIVE_MAGIC;
  _version = CURRENT_CDS_ARCHIVE_VERSION;
  _alignment = alignment;
  _obj_alignment = ObjectAlignmentInBytes;
  _compact_strings = CompactStrings;
  _narrow_oop_mode = Universe::narrow_oop_mode();
  _narrow_oop_base = Universe::narrow_oop_base();
  _narrow_oop_shift = Universe::narrow_oop_shift();
  _max_heap_size = MaxHeapSize;
  _narrow_klass_base = Universe::narrow_klass_base();
  _narrow_klass_shift = Universe::narrow_klass_shift();
  _shared_path_table_size = mapinfo->_shared_path_table_size;
  _shared_path_table = mapinfo->_shared_path_table;
  _shared_path_entry_size = mapinfo->_shared_path_entry_size;
  if (HeapShared::is_heap_object_archiving_allowed()) {
    _heap_reserved = Universe::heap()->reserved_region();
  }

  // The following fields are for sanity checks for whether this archive
  // will function correctly with this JVM and the bootclasspath it's
  // invoked with.

  // JVM version string ... changes on each build.
  get_header_version(_jvm_ident);

  ClassLoaderExt::finalize_shared_paths_misc_info();
  _app_class_paths_start_index = ClassLoaderExt::app_class_paths_start_index();
  _app_module_paths_start_index = ClassLoaderExt::app_module_paths_start_index();
  _max_used_path_index = ClassLoaderExt::max_used_path_index();

  _verify_local = BytecodeVerificationLocal;
  _verify_remote = BytecodeVerificationRemote;
  _has_platform_or_app_classes = ClassLoaderExt::has_platform_or_app_classes();
  _shared_base_address = SharedBaseAddress;
  _allow_archiving_with_java_agent = AllowArchivingWithJavaAgent;
}

void SharedClassPathEntry::init(const char* name, bool is_modules_image, TRAPS) {
  assert(DumpSharedSpaces, "dump time only");
  _timestamp = 0;
  _filesize  = 0;

  struct stat st;
  if (os::stat(name, &st) == 0) {
    if ((st.st_mode & S_IFMT) == S_IFDIR) {
      _type = dir_entry;
    } else {
      // The timestamp of the modules_image is not checked at runtime.
      if (is_modules_image) {
        _type = modules_image_entry;
      } else {
        _type = jar_entry;
        _timestamp = st.st_mtime;
      }
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

bool SharedClassPathEntry::validate(bool is_class_path) {
  assert(UseSharedSpaces, "runtime only");

  struct stat st;
  const char* name;

  // In order to validate the runtime modules image file size against the archived
  // size information, we need to obtain the runtime modules image path. The recorded
  // dump time modules image path in the archive may be different from the runtime path
  // if the JDK image has beed moved after generating the archive.
  if (is_modules_image()) {
    name = ClassLoader::get_jrt_entry()->name();
  } else {
    name = this->name();
  }

  bool ok = true;
  log_info(class, path)("checking shared classpath entry: %s", name);
  if (os::stat(name, &st) != 0 && is_class_path) {
    // If the archived module path entry does not exist at runtime, it is not fatal
    // (no need to invalid the shared archive) because the shared runtime visibility check
    // filters out any archived module classes that do not have a matching runtime
    // module path location.
    FileMapInfo::fail_continue("Required classpath entry does not exist: %s", name);
    ok = false;
  } else if (is_dir()) {
    if (!os::dir_is_empty(name)) {
      FileMapInfo::fail_continue("directory is not empty: %s", name);
      ok = false;
    }
  } else if ((has_timestamp() && _timestamp != st.st_mtime) ||
             _filesize != st.st_size) {
    ok = false;
    if (PrintSharedArchiveAndExit) {
      FileMapInfo::fail_continue(_timestamp != st.st_mtime ?
                                 "Timestamp mismatch" :
                                 "File size mismatch");
    } else {
      FileMapInfo::fail_continue("A jar file is not the one used while building"
                                 " the shared archive file: %s", name);
    }
  }

  if (PrintSharedArchiveAndExit && !ok) {
    // If PrintSharedArchiveAndExit is enabled, don't report failure to the
    // caller. Please see above comments for more details.
    ok = true;
  }
  return ok;
}

void SharedClassPathEntry::metaspace_pointers_do(MetaspaceClosure* it) {
  it->push(&_name);
  it->push(&_manifest);
}

void FileMapInfo::allocate_shared_path_table() {
  assert(DumpSharedSpaces, "Sanity");

  Thread* THREAD = Thread::current();
  ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
  ClassPathEntry* jrt = ClassLoader::get_jrt_entry();

  assert(jrt != NULL,
         "No modular java runtime image present when allocating the CDS classpath entry table");

  size_t entry_size = sizeof(SharedClassPathEntry); // assert ( should be 8 byte aligned??)
  int num_boot_classpath_entries = ClassLoader::num_boot_classpath_entries();
  int num_app_classpath_entries = ClassLoader::num_app_classpath_entries();
  int num_module_path_entries = ClassLoader::num_module_path_entries();
  int num_entries = num_boot_classpath_entries + num_app_classpath_entries + num_module_path_entries;
  size_t bytes = entry_size * num_entries;

  _shared_path_table = MetadataFactory::new_array<u8>(loader_data, (int)(bytes + 7 / 8), THREAD);
  _shared_path_table_size = num_entries;
  _shared_path_entry_size = entry_size;

  // 1. boot class path
  int i = 0;
  ClassPathEntry* cpe = jrt;
  while (cpe != NULL) {
    bool is_jrt = (cpe == jrt);
    const char* type = (is_jrt ? "jrt" : (cpe->is_jar_file() ? "jar" : "dir"));
    log_info(class, path)("add main shared path (%s) %s", type, cpe->name());
    SharedClassPathEntry* ent = shared_path(i);
    ent->init(cpe->name(), is_jrt, THREAD);
    if (!is_jrt) {    // No need to do the modules image.
      EXCEPTION_MARK; // The following call should never throw, but would exit VM on error.
      update_shared_classpath(cpe, ent, THREAD);
    }
    cpe = ClassLoader::get_next_boot_classpath_entry(cpe);
    i++;
  }
  assert(i == num_boot_classpath_entries,
         "number of boot class path entry mismatch");

  // 2. app class path
  ClassPathEntry *acpe = ClassLoader::app_classpath_entries();
  while (acpe != NULL) {
    log_info(class, path)("add app shared path %s", acpe->name());
    SharedClassPathEntry* ent = shared_path(i);
    ent->init(acpe->name(), false, THREAD);
    EXCEPTION_MARK;
    update_shared_classpath(acpe, ent, THREAD);
    acpe = acpe->next();
    i++;
  }

  // 3. module path
  ClassPathEntry *mpe = ClassLoader::module_path_entries();
  while (mpe != NULL) {
    log_info(class, path)("add module path %s",mpe->name());
    SharedClassPathEntry* ent = shared_path(i);
    ent->init(mpe->name(), false, THREAD);
    EXCEPTION_MARK;
    update_shared_classpath(mpe, ent, THREAD);
    mpe = mpe->next();
    i++;
  }
  assert(i == num_entries, "number of shared path entry mismatch");
}

void FileMapInfo::check_nonempty_dir_in_shared_path_table() {
  assert(DumpSharedSpaces, "dump time only");

  bool has_nonempty_dir = false;

  int last = _shared_path_table_size - 1;
  if (last > ClassLoaderExt::max_used_path_index()) {
     // no need to check any path beyond max_used_path_index
     last = ClassLoaderExt::max_used_path_index();
  }

  for (int i = 0; i <= last; i++) {
    SharedClassPathEntry *e = shared_path(i);
    if (e->is_dir()) {
      const char* path = e->name();
      if (!os::dir_is_empty(path)) {
        tty->print_cr("Error: non-empty directory '%s'", path);
        has_nonempty_dir = true;
      }
    }
  }

  if (has_nonempty_dir) {
    ClassLoader::exit_with_path_failure("Cannot have non-empty directory in paths", NULL);
  }
}

class ManifestStream: public ResourceObj {
  private:
  u1*   _buffer_start; // Buffer bottom
  u1*   _buffer_end;   // Buffer top (one past last element)
  u1*   _current;      // Current buffer position

 public:
  // Constructor
  ManifestStream(u1* buffer, int length) : _buffer_start(buffer),
                                           _current(buffer) {
    _buffer_end = buffer + length;
  }

  static bool is_attr(u1* attr, const char* name) {
    return strncmp((const char*)attr, name, strlen(name)) == 0;
  }

  static char* copy_attr(u1* value, size_t len) {
    char* buf = NEW_RESOURCE_ARRAY(char, len + 1);
    strncpy(buf, (char*)value, len);
    buf[len] = 0;
    return buf;
  }

  // The return value indicates if the JAR is signed or not
  bool check_is_signed() {
    u1* attr = _current;
    bool isSigned = false;
    while (_current < _buffer_end) {
      if (*_current == '\n') {
        *_current = '\0';
        u1* value = (u1*)strchr((char*)attr, ':');
        if (value != NULL) {
          assert(*(value+1) == ' ', "Unrecognized format" );
          if (strstr((char*)attr, "-Digest") != NULL) {
            isSigned = true;
            break;
          }
        }
        *_current = '\n'; // restore
        attr = _current + 1;
      }
      _current ++;
    }
    return isSigned;
  }
};

void FileMapInfo::update_shared_classpath(ClassPathEntry *cpe, SharedClassPathEntry* ent, TRAPS) {
  ClassLoaderData* loader_data = ClassLoaderData::the_null_class_loader_data();
  ResourceMark rm(THREAD);
  jint manifest_size;

  if (cpe->is_jar_file()) {
    assert(ent->is_jar(), "the shared class path entry is not a JAR file");
    char* manifest = ClassLoaderExt::read_manifest(cpe, &manifest_size, CHECK);
    if (manifest != NULL) {
      ManifestStream* stream = new ManifestStream((u1*)manifest,
                                                  manifest_size);
      if (stream->check_is_signed()) {
        ent->set_is_signed();
      } else {
        // Copy the manifest into the shared archive
        manifest = ClassLoaderExt::read_raw_manifest(cpe, &manifest_size, CHECK);
        Array<u1>* buf = MetadataFactory::new_array<u1>(loader_data,
                                                        manifest_size,
                                                        THREAD);
        char* p = (char*)(buf->data());
        memcpy(p, manifest, manifest_size);
        ent->set_manifest(buf);
      }
    }
  }
}


bool FileMapInfo::validate_shared_path_table() {
  assert(UseSharedSpaces, "runtime only");

  _validating_shared_path_table = true;
  _shared_path_table = _header->_shared_path_table;
  _shared_path_entry_size = _header->_shared_path_entry_size;
  _shared_path_table_size = _header->_shared_path_table_size;

  int module_paths_start_index = _header->_app_module_paths_start_index;

  // validate the path entries up to the _max_used_path_index
  for (int i=0; i < _header->_max_used_path_index + 1; i++) {
    if (i < module_paths_start_index) {
      if (shared_path(i)->validate()) {
        log_info(class, path)("ok");
      } else {
        assert(!UseSharedSpaces, "UseSharedSpaces should be disabled");
        return false;
      }
    } else if (i >= module_paths_start_index) {
      if (shared_path(i)->validate(false /* not a class path entry */)) {
        log_info(class, path)("ok");
      } else {
        assert(!UseSharedSpaces, "UseSharedSpaces should be disabled");
        return false;
      }
    }
  }

  _validating_shared_path_table = false;
  return true;
}

// Read the FileMapInfo information from the file.

bool FileMapInfo::init_from_file(int fd) {
  size_t sz = sizeof(FileMapHeader);
  size_t n = os::read(fd, _header, (unsigned int)sz);
  if (n != sz) {
    fail_continue("Unable to read the file header.");
    return false;
  }
  if (_header->_version != CURRENT_CDS_ARCHIVE_VERSION) {
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
  CDSFileMapRegion* si = space_at(MetaspaceShared::last_valid_region);
  // The last space might be empty
  if (si->_file_offset > len || len - si->_file_offset < si->_used) {
    fail_continue("The shared archive file has been truncated.");
    return false;
  }

  _file_offset += (long)n;
  SharedBaseAddress = _header->_shared_base_address;
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
  LogMessage(cds) msg;
  if (msg.is_info()) {
    msg.info("Dumping shared data to file: ");
    msg.info("   %s", _full_path);
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
  write_bytes(_header, sizeof(FileMapHeader));
  write_bytes(ClassLoader::get_shared_paths_misc_info(), (size_t)info_size);
  align_file_position();
}


// Dump region to file.

void FileMapInfo::write_region(int region, char* base, size_t size,
                               bool read_only, bool allow_exec) {
  CDSFileMapRegion* si = space_at(region);

  if (_file_open) {
    guarantee(si->_file_offset == _file_offset, "file offset mismatch.");
    log_info(cds)("Shared file region %d: " SIZE_FORMAT_HEX_W(08)
                  " bytes, addr " INTPTR_FORMAT " file offset " SIZE_FORMAT_HEX_W(08),
                  region, size, p2i(base), _file_offset);
  } else {
    si->_file_offset = _file_offset;
  }
  if (HeapShared::is_heap_region(region)) {
    assert((base - (char*)Universe::narrow_oop_base()) % HeapWordSize == 0, "Sanity");
    if (base != NULL) {
      si->_addr._offset = (intx)CompressedOops::encode_not_null((oop)base);
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
  if (base != NULL) {
    write_bytes_aligned(base, size);
  }
}

// Write out the given archive heap memory regions.  GC code combines multiple
// consecutive archive GC regions into one MemRegion whenever possible and
// produces the 'heap_mem' array.
//
// If the archive heap memory size is smaller than a single dump time GC region
// size, there is only one MemRegion in the array.
//
// If the archive heap memory size is bigger than one dump time GC region size,
// the 'heap_mem' array may contain more than one consolidated MemRegions. When
// the first/bottom archive GC region is a partial GC region (with the empty
// portion at the higher address within the region), one MemRegion is used for
// the bottom partial archive GC region. The rest of the consecutive archive
// GC regions are combined into another MemRegion.
//
// Here's the mapping from (archive heap GC regions) -> (GrowableArray<MemRegion> *regions).
//   + We have 1 or more archive heap regions: ah0, ah1, ah2 ..... ahn
//   + We have 1 or 2 consolidated heap memory regions: r0 and r1
//
// If there's a single archive GC region (ah0), then r0 == ah0, and r1 is empty.
// Otherwise:
//
// "X" represented space that's occupied by heap objects.
// "_" represented unused spaced in the heap region.
//
//
//    |ah0       | ah1 | ah2| ...... | ahn|
//    |XXXXXX|__ |XXXXX|XXXX|XXXXXXXX|XXXX|
//    |<-r0->|   |<- r1 ----------------->|
//            ^^^
//             |
//             +-- gap
size_t FileMapInfo::write_archive_heap_regions(GrowableArray<MemRegion> *heap_mem,
                                               GrowableArray<ArchiveHeapOopmapInfo> *oopmaps,
                                               int first_region_id, int max_num_regions,
                                               bool print_log) {
  assert(max_num_regions <= 2, "Only support maximum 2 memory regions");

  int arr_len = heap_mem == NULL ? 0 : heap_mem->length();
  if(arr_len > max_num_regions) {
    fail_stop("Unable to write archive heap memory regions: "
              "number of memory regions exceeds maximum due to fragmentation. "
              "Please increase java heap size "
              "(current MaxHeapSize is " SIZE_FORMAT ", InitialHeapSize is " SIZE_FORMAT ").",
              MaxHeapSize, InitialHeapSize);
  }

  size_t total_size = 0;
  for (int i = first_region_id, arr_idx = 0;
           i < first_region_id + max_num_regions;
           i++, arr_idx++) {
    char* start = NULL;
    size_t size = 0;
    if (arr_idx < arr_len) {
      start = (char*)heap_mem->at(arr_idx).start();
      size = heap_mem->at(arr_idx).byte_size();
      total_size += size;
    }

    if (print_log) {
      log_info(cds)("Archive heap region %d " INTPTR_FORMAT " - " INTPTR_FORMAT " = " SIZE_FORMAT_W(8) " bytes",
                    i, p2i(start), p2i(start + size), size);
    }
    write_region(i, start, size, false, false);
    if (size > 0) {
      space_at(i)->_oopmap = oopmaps->at(arr_idx)._oopmap;
      space_at(i)->_oopmap_size_in_bits = oopmaps->at(arr_idx)._oopmap_size_in_bits;
    }
  }
  return total_size;
}

// Dump bytes to file -- at the current file position.

void FileMapInfo::write_bytes(const void* buffer, size_t nbytes) {
  if (_file_open) {
    size_t n = os::write(_fd, buffer, (unsigned int)nbytes);
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

void FileMapInfo::write_bytes_aligned(const void* buffer, size_t nbytes) {
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
  CDSFileMapRegion* si = space_at(idx);
  if (!si->_read_only) {
    // the space is already readwrite so we are done
    return true;
  }
  size_t used = si->_used;
  size_t size = align_up(used, os::vm_allocation_granularity());
  if (!open_for_read()) {
    return false;
  }
  char *addr = region_addr(idx);
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
  char* requested_addr = region_addr(0);
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
static const char* shared_region_name[] = { "MiscData", "ReadWrite", "ReadOnly", "MiscCode", "OptionalData",
                                            "String1", "String2", "OpenArchive1", "OpenArchive2" };

char* FileMapInfo::map_region(int i, char** top_ret) {
  assert(!HeapShared::is_heap_region(i), "sanity");
  CDSFileMapRegion* si = space_at(i);
  size_t used = si->_used;
  size_t alignment = os::vm_allocation_granularity();
  size_t size = align_up(used, alignment);
  char *requested_addr = region_addr(i);

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


  if (!verify_region_checksum(i)) {
    return NULL;
  }

  *top_ret = base + size;
  return base;
}

address FileMapInfo::decode_start_address(CDSFileMapRegion* spc, bool with_current_oop_encoding_mode) {
  if (with_current_oop_encoding_mode) {
    return (address)CompressedOops::decode_not_null(offset_of_space(spc));
  } else {
    return (address)HeapShared::decode_from_archive(offset_of_space(spc));
  }
}

static MemRegion *closed_archive_heap_ranges = NULL;
static MemRegion *open_archive_heap_ranges = NULL;
static int num_closed_archive_heap_ranges = 0;
static int num_open_archive_heap_ranges = 0;

#if INCLUDE_CDS_JAVA_HEAP
bool FileMapInfo::has_heap_regions() {
  return (_header->_space[MetaspaceShared::first_closed_archive_heap_region]._used > 0);
}

// Returns the address range of the archived heap regions computed using the
// current oop encoding mode. This range may be different than the one seen at
// dump time due to encoding mode differences. The result is used in determining
// if/how these regions should be relocated at run time.
MemRegion FileMapInfo::get_heap_regions_range_with_current_oop_encoding_mode() {
  address start = (address) max_uintx;
  address end   = NULL;

  for (int i = MetaspaceShared::first_closed_archive_heap_region;
           i <= MetaspaceShared::last_valid_region;
           i++) {
    CDSFileMapRegion* si = space_at(i);
    size_t size = si->_used;
    if (size > 0) {
      address s = start_address_as_decoded_with_current_oop_encoding_mode(si);
      address e = s + size;
      if (start > s) {
        start = s;
      }
      if (end < e) {
        end = e;
      }
    }
  }
  assert(end != NULL, "must have at least one used heap region");
  return MemRegion((HeapWord*)start, (HeapWord*)end);
}

//
// Map the closed and open archive heap objects to the runtime java heap.
//
// The shared objects are mapped at (or close to ) the java heap top in
// closed archive regions. The mapped objects contain no out-going
// references to any other java heap regions. GC does not write into the
// mapped closed archive heap region.
//
// The open archive heap objects are mapped below the shared objects in
// the runtime java heap. The mapped open archive heap data only contains
// references to the shared objects and open archive objects initially.
// During runtime execution, out-going references to any other java heap
// regions may be added. GC may mark and update references in the mapped
// open archive objects.
void FileMapInfo::map_heap_regions_impl() {
  if (!HeapShared::is_heap_object_archiving_allowed()) {
    log_info(cds)("CDS heap data is being ignored. UseG1GC, "
                  "UseCompressedOops and UseCompressedClassPointers are required.");
    return;
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

  MemRegion heap_reserved = Universe::heap()->reserved_region();

  log_info(cds)("CDS archive was created with max heap size = " SIZE_FORMAT "M, and the following configuration:",
                max_heap_size()/M);
  log_info(cds)("    narrow_klass_base = " PTR_FORMAT ", narrow_klass_shift = %d",
                p2i(narrow_klass_base()), narrow_klass_shift());
  log_info(cds)("    narrow_oop_mode = %d, narrow_oop_base = " PTR_FORMAT ", narrow_oop_shift = %d",
                narrow_oop_mode(), p2i(narrow_oop_base()), narrow_oop_shift());

  log_info(cds)("The current max heap size = " SIZE_FORMAT "M, HeapRegion::GrainBytes = " SIZE_FORMAT,
                heap_reserved.byte_size()/M, HeapRegion::GrainBytes);
  log_info(cds)("    narrow_klass_base = " PTR_FORMAT ", narrow_klass_shift = %d",
                p2i(Universe::narrow_klass_base()), Universe::narrow_klass_shift());
  log_info(cds)("    narrow_oop_mode = %d, narrow_oop_base = " PTR_FORMAT ", narrow_oop_shift = %d",
                Universe::narrow_oop_mode(), p2i(Universe::narrow_oop_base()), Universe::narrow_oop_shift());

  if (narrow_klass_base() != Universe::narrow_klass_base() ||
      narrow_klass_shift() != Universe::narrow_klass_shift()) {
    log_info(cds)("CDS heap data cannot be used because the archive was created with an incompatible narrow klass encoding mode.");
    return;
  }

  if (narrow_oop_mode() != Universe::narrow_oop_mode() ||
      narrow_oop_base() != Universe::narrow_oop_base() ||
      narrow_oop_shift() != Universe::narrow_oop_shift()) {
    log_info(cds)("CDS heap data need to be relocated because the archive was created with an incompatible oop encoding mode.");
    _heap_pointers_need_patching = true;
  } else {
    MemRegion range = get_heap_regions_range_with_current_oop_encoding_mode();
    if (!heap_reserved.contains(range)) {
      log_info(cds)("CDS heap data need to be relocated because");
      log_info(cds)("the desired range " PTR_FORMAT " - "  PTR_FORMAT, p2i(range.start()), p2i(range.end()));
      log_info(cds)("is outside of the heap " PTR_FORMAT " - "  PTR_FORMAT, p2i(heap_reserved.start()), p2i(heap_reserved.end()));
      _heap_pointers_need_patching = true;
    }
  }

  ptrdiff_t delta = 0;
  if (_heap_pointers_need_patching) {
    //   dumptime heap end  ------------v
    //   [      |archived heap regions| ]         runtime heap end ------v
    //                                       [   |archived heap regions| ]
    //                                  |<-----delta-------------------->|
    //
    // At dump time, the archived heap regions were near the top of the heap.
    // At run time, they may not be inside the heap, so we move them so
    // that they are now near the top of the runtime time. This can be done by
    // the simple math of adding the delta as shown above.
    address dumptime_heap_end = (address)_header->_heap_reserved.end();
    address runtime_heap_end = (address)heap_reserved.end();
    delta = runtime_heap_end - dumptime_heap_end;
  }

  log_info(cds)("CDS heap data relocation delta = " INTX_FORMAT " bytes", delta);
  HeapShared::init_narrow_oop_decoding(narrow_oop_base() + delta, narrow_oop_shift());

  CDSFileMapRegion* si = space_at(MetaspaceShared::first_closed_archive_heap_region);
  address relocated_closed_heap_region_bottom = start_address_as_decoded_from_archive(si);
  if (!is_aligned(relocated_closed_heap_region_bottom, HeapRegion::GrainBytes)) {
    // Align the bottom of the closed archive heap regions at G1 region boundary.
    // This will avoid the situation where the highest open region and the lowest
    // closed region sharing the same G1 region. Otherwise we will fail to map the
    // open regions.
    size_t align = size_t(relocated_closed_heap_region_bottom) % HeapRegion::GrainBytes;
    delta -= align;
    log_info(cds)("CDS heap data need to be relocated lower by a further " SIZE_FORMAT
                  " bytes to " INTX_FORMAT " to be aligned with HeapRegion::GrainBytes",
                  align, delta);
    HeapShared::init_narrow_oop_decoding(narrow_oop_base() + delta, narrow_oop_shift());
    _heap_pointers_need_patching = true;
    relocated_closed_heap_region_bottom = start_address_as_decoded_from_archive(si);
  }
  assert(is_aligned(relocated_closed_heap_region_bottom, HeapRegion::GrainBytes),
         "must be");

  // Map the closed_archive_heap regions, GC does not write into the regions.
  if (map_heap_data(&closed_archive_heap_ranges,
                    MetaspaceShared::first_closed_archive_heap_region,
                    MetaspaceShared::max_closed_archive_heap_region,
                    &num_closed_archive_heap_ranges)) {
    HeapShared::set_closed_archive_heap_region_mapped();

    // Now, map open_archive heap regions, GC can write into the regions.
    if (map_heap_data(&open_archive_heap_ranges,
                      MetaspaceShared::first_open_archive_heap_region,
                      MetaspaceShared::max_open_archive_heap_region,
                      &num_open_archive_heap_ranges,
                      true /* open */)) {
      HeapShared::set_open_archive_heap_region_mapped();
    }
  }
}

void FileMapInfo::map_heap_regions() {
  if (has_heap_regions()) {
    map_heap_regions_impl();
  }

  if (!HeapShared::closed_archive_heap_region_mapped()) {
    assert(closed_archive_heap_ranges == NULL &&
           num_closed_archive_heap_ranges == 0, "sanity");
  }

  if (!HeapShared::open_archive_heap_region_mapped()) {
    assert(open_archive_heap_ranges == NULL && num_open_archive_heap_ranges == 0, "sanity");
  }
}

bool FileMapInfo::map_heap_data(MemRegion **heap_mem, int first,
                                int max, int* num, bool is_open_archive) {
  MemRegion * regions = new MemRegion[max];
  CDSFileMapRegion* si;
  int region_num = 0;

  for (int i = first;
           i < first + max; i++) {
    si = space_at(i);
    size_t size = si->_used;
    if (size > 0) {
      HeapWord* start = (HeapWord*)start_address_as_decoded_from_archive(si);
      regions[region_num] = MemRegion(start, size / HeapWordSize);
      region_num ++;
      log_info(cds)("Trying to map heap data: region[%d] at " INTPTR_FORMAT ", size = " SIZE_FORMAT_W(8) " bytes",
                    i, p2i(start), size);
    }
  }

  if (region_num == 0) {
    return false; // no archived java heap data
  }

  // Check that ranges are within the java heap
  if (!G1CollectedHeap::heap()->check_archive_addresses(regions, region_num)) {
    log_info(cds)("UseSharedSpaces: Unable to allocate region, range is not within java heap.");
    return false;
  }

  // allocate from java heap
  if (!G1CollectedHeap::heap()->alloc_archive_regions(
             regions, region_num, is_open_archive)) {
    log_info(cds)("UseSharedSpaces: Unable to allocate region, java heap range is already in use.");
    return false;
  }

  // Map the archived heap data. No need to call MemTracker::record_virtual_memory_type()
  // for mapped regions as they are part of the reserved java heap, which is
  // already recorded.
  for (int i = 0; i < region_num; i++) {
    si = space_at(first + i);
    char* addr = (char*)regions[i].start();
    char* base = os::map_memory(_fd, _full_path, si->_file_offset,
                                addr, regions[i].byte_size(), si->_read_only,
                                si->_allow_exec);
    if (base == NULL || base != addr) {
      // dealloc the regions from java heap
      dealloc_archive_heap_regions(regions, region_num, is_open_archive);
      log_info(cds)("UseSharedSpaces: Unable to map at required address in java heap. "
                    INTPTR_FORMAT ", size = " SIZE_FORMAT " bytes",
                    p2i(addr), regions[i].byte_size());
      return false;
    }
  }

  if (!verify_mapped_heap_regions(first, region_num)) {
    // dealloc the regions from java heap
    dealloc_archive_heap_regions(regions, region_num, is_open_archive);
    log_info(cds)("UseSharedSpaces: mapped heap regions are corrupt");
    return false;
  }

  // the shared heap data is mapped successfully
  *heap_mem = regions;
  *num = region_num;
  return true;
}

bool FileMapInfo::verify_mapped_heap_regions(int first, int num) {
  assert(num > 0, "sanity");
  for (int i = first; i < first + num; i++) {
    if (!verify_region_checksum(i)) {
      return false;
    }
  }
  return true;
}

void FileMapInfo::patch_archived_heap_embedded_pointers() {
  if (!_heap_pointers_need_patching) {
    return;
  }

  patch_archived_heap_embedded_pointers(closed_archive_heap_ranges,
                                        num_closed_archive_heap_ranges,
                                        MetaspaceShared::first_closed_archive_heap_region);

  patch_archived_heap_embedded_pointers(open_archive_heap_ranges,
                                        num_open_archive_heap_ranges,
                                        MetaspaceShared::first_open_archive_heap_region);
}

void FileMapInfo::patch_archived_heap_embedded_pointers(MemRegion* ranges, int num_ranges,
                                                        int first_region_idx) {
  for (int i=0; i<num_ranges; i++) {
    CDSFileMapRegion* si = space_at(i + first_region_idx);
    HeapShared::patch_archived_heap_embedded_pointers(ranges[i], (address)si->_oopmap,
                                                      si->_oopmap_size_in_bits);
  }
}

// This internally allocates objects using SystemDictionary::Object_klass(), so it
// must be called after the well-known classes are resolved.
void FileMapInfo::fixup_mapped_heap_regions() {
  // If any closed regions were found, call the fill routine to make them parseable.
  // Note that closed_archive_heap_ranges may be non-NULL even if no ranges were found.
  if (num_closed_archive_heap_ranges != 0) {
    assert(closed_archive_heap_ranges != NULL,
           "Null closed_archive_heap_ranges array with non-zero count");
    G1CollectedHeap::heap()->fill_archive_regions(closed_archive_heap_ranges,
                                                  num_closed_archive_heap_ranges);
  }

  // do the same for mapped open archive heap regions
  if (num_open_archive_heap_ranges != 0) {
    assert(open_archive_heap_ranges != NULL, "NULL open_archive_heap_ranges array with non-zero count");
    G1CollectedHeap::heap()->fill_archive_regions(open_archive_heap_ranges,
                                                  num_open_archive_heap_ranges);
  }
}

// dealloc the archive regions from java heap
void FileMapInfo::dealloc_archive_heap_regions(MemRegion* regions, int num, bool is_open) {
  if (num > 0) {
    assert(regions != NULL, "Null archive ranges array with non-zero count");
    G1CollectedHeap::heap()->dealloc_archive_regions(regions, num, is_open);
  }
}
#endif // INCLUDE_CDS_JAVA_HEAP

bool FileMapInfo::verify_region_checksum(int i) {
  if (!VerifySharedSpaces) {
    return true;
  }

  size_t sz = space_at(i)->_used;

  if (sz == 0) {
    return true; // no data
  }
  if ((HeapShared::is_closed_archive_heap_region(i) &&
       !HeapShared::closed_archive_heap_region_mapped()) ||
      (HeapShared::is_open_archive_heap_region(i) &&
       !HeapShared::open_archive_heap_region_mapped())) {
    return true; // archived heap data is not mapped
  }
  const char* buf = region_addr(i);
  int crc = ClassLoader::crc32(0, buf, (jint)sz);
  if (crc != space_at(i)->_crc) {
    fail_continue("Checksum verification failed.");
    return false;
  }
  return true;
}

// Unmap a memory region in the address space.

void FileMapInfo::unmap_region(int i) {
  assert(!HeapShared::is_heap_region(i), "sanity");
  CDSFileMapRegion* si = space_at(i);
  size_t used = si->_used;
  size_t size = align_up(used, os::vm_allocation_granularity());

  if (used == 0) {
    return;
  }

  char* addr = region_addr(i);
  if (!os::unmap_memory(addr, size)) {
    fail_stop("Unable to unmap shared space.");
  }
}

void FileMapInfo::assert_mark(bool check) {
  if (!check) {
    fail_stop("Mark mismatch while restoring from shared file.");
  }
}

void FileMapInfo::metaspace_pointers_do(MetaspaceClosure* it) {
  it->push(&_shared_path_table);
  for (int i=0; i<_shared_path_table_size; i++) {
    shared_path(i)->metaspace_pointers_do(it);
  }
}


FileMapInfo* FileMapInfo::_current_info = NULL;
bool FileMapInfo::_heap_pointers_need_patching = false;
Array<u8>* FileMapInfo::_shared_path_table = NULL;
int FileMapInfo::_shared_path_table_size = 0;
size_t FileMapInfo::_shared_path_entry_size = 0x1234baad;
bool FileMapInfo::_validating_shared_path_table = false;

// Open the shared archive file, read and validate the header
// information (version, boot classpath, etc.).  If initialization
// fails, shared spaces are disabled and the file is closed. [See
// fail_continue.]
//
// Validation of the archive is done in two steps:
//
// [1] validate_header() - done here. This checks the header, including _paths_misc_info.
// [2] validate_shared_path_table - this is done later, because the table is in the RW
//     region of the archive, which is not mapped yet.
bool FileMapInfo::initialize() {
  assert(UseSharedSpaces, "UseSharedSpaces expected.");

  if (JvmtiExport::should_post_class_file_load_hook() && JvmtiExport::has_early_class_hook_env()) {
    // CDS assumes that no classes resolved in SystemDictionary::resolve_well_known_classes
    // are replaced at runtime by JVMTI ClassFileLoadHook. All of those classes are resolved
    // during the JVMTI "early" stage, so we can still use CDS if
    // JvmtiExport::has_early_class_hook_env() is false.
    FileMapInfo::fail_continue("CDS is disabled because early JVMTI ClassFileLoadHook is in use.");
    return false;
  }

  if (!open_for_read()) {
    return false;
  }

  init_from_file(_fd);
  if (!validate_header()) {
    return false;
  }
  return true;
}

char* FileMapInfo::region_addr(int idx) {
  CDSFileMapRegion* si = space_at(idx);
  if (HeapShared::is_heap_region(idx)) {
    assert(DumpSharedSpaces, "The following doesn't work at runtime");
    return si->_used > 0 ?
          (char*)start_address_as_decoded_with_current_oop_encoding_mode(si) : NULL;
  } else {
    return si->_addr._base;
  }
}

int FileMapHeader::compute_crc() {
  char* start = (char*)this;
  // start computing from the field after _crc
  char* buf = (char*)&_crc + sizeof(_crc);
  size_t sz = sizeof(FileMapHeader) - (buf - start);
  int crc = ClassLoader::crc32(0, buf, (jint)sz);
  return crc;
}

// This function should only be called during run time with UseSharedSpaces enabled.
bool FileMapHeader::validate() {
  if (VerifySharedSpaces && compute_crc() != _crc) {
    FileMapInfo::fail_continue("Header checksum verification failed.");
    return false;
  }

  if (!Arguments::has_jimage()) {
    FileMapInfo::fail_continue("The shared archive file cannot be used with an exploded module build.");
    return false;
  }

  if (_version != CURRENT_CDS_ARCHIVE_VERSION) {
    FileMapInfo::fail_continue("The shared archive file is the wrong version.");
    return false;
  }
  if (_magic != CDS_ARCHIVE_MAGIC) {
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

  // This must be done after header validation because it might change the
  // header data
  const char* prop = Arguments::get_property("java.system.class.loader");
  if (prop != NULL) {
    warning("Archived non-system classes are disabled because the "
            "java.system.class.loader property is specified (value = \"%s\"). "
            "To use archived non-system classes, this property must not be set", prop);
    _has_platform_or_app_classes = false;
  }

  // For backwards compatibility, we don't check the verification setting
  // if the archive only contains system classes.
  if (_has_platform_or_app_classes &&
      ((!_verify_local && BytecodeVerificationLocal) ||
       (!_verify_remote && BytecodeVerificationRemote))) {
    FileMapInfo::fail_continue("The shared archive file was created with less restrictive "
                  "verification setting than the current setting.");
    return false;
  }

  // Java agents are allowed during run time. Therefore, the following condition is not
  // checked: (!_allow_archiving_with_java_agent && AllowArchivingWithJavaAgent)
  // Note: _allow_archiving_with_java_agent is set in the shared archive during dump time
  // while AllowArchivingWithJavaAgent is set during the current run.
  if (_allow_archiving_with_java_agent && !AllowArchivingWithJavaAgent) {
    FileMapInfo::fail_continue("The setting of the AllowArchivingWithJavaAgent is different "
                               "from the setting in the shared archive.");
    return false;
  }

  if (_allow_archiving_with_java_agent) {
    warning("This archive was created with AllowArchivingWithJavaAgent. It should be used "
            "for testing purposes only and should not be used in a production environment");
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

// Check if a given address is within one of the shared regions
bool FileMapInfo::is_in_shared_region(const void* p, int idx) {
  assert(idx == MetaspaceShared::ro ||
         idx == MetaspaceShared::rw ||
         idx == MetaspaceShared::mc ||
         idx == MetaspaceShared::md, "invalid region index");
  char* base = region_addr(idx);
  if (p >= base && p < base + space_at(idx)->_used) {
    return true;
  }
  return false;
}

// Unmap mapped regions of shared space.
void FileMapInfo::stop_sharing_and_unmap(const char* msg) {
  MetaspaceObj::set_shared_metaspace_range(NULL, NULL);

  FileMapInfo *map_info = FileMapInfo::current_info();
  if (map_info) {
    map_info->fail_continue("%s", msg);
    for (int i = 0; i < MetaspaceShared::num_non_heap_spaces; i++) {
      if (!HeapShared::is_heap_region(i)) {
        char *addr = map_info->region_addr(i);
        if (addr != NULL) {
          map_info->unmap_region(i);
          map_info->space_at(i)->_addr._base = NULL;
        }
      }
    }
    // Dealloc the archive heap regions only without unmapping. The regions are part
    // of the java heap. Unmapping of the heap regions are managed by GC.
    map_info->dealloc_archive_heap_regions(open_archive_heap_ranges,
                                           num_open_archive_heap_ranges,
                                           true);
    map_info->dealloc_archive_heap_regions(closed_archive_heap_ranges,
                                           num_closed_archive_heap_ranges,
                                           false);
  } else if (DumpSharedSpaces) {
    fail_stop("%s", msg);
  }
}
