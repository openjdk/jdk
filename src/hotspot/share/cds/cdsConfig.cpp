/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/archiveHeapLoader.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/heapShared.hpp"
#include "classfile/classLoaderDataShared.hpp"
#include "include/jvm_io.h"
#include "logging/log.hpp"
#include "runtime/arguments.hpp"
#include "runtime/java.hpp"
#include "utilities/defaultStream.hpp"

bool CDSConfig::_is_dumping_static_archive = false;
bool CDSConfig::_is_dumping_dynamic_archive = false;

// The ability to dump the FMG depends on many factors checked by
// is_dumping_full_module_graph(), but can be unconditionally disabled by
// _dumping_full_module_graph_disabled. (Ditto for loading the FMG).
bool CDSConfig::_dumping_full_module_graph_disabled = false;
bool CDSConfig::_loading_full_module_graph_disabled = false;

char* CDSConfig::_default_archive_path = nullptr;
char* CDSConfig::SharedArchivePath = nullptr;
char* CDSConfig::SharedDynamicArchivePath = nullptr;

char* CDSConfig::get_default_archive_path() {
  if (_default_archive_path == nullptr) {
    char jvm_path[JVM_MAXPATHLEN];
    os::jvm_path(jvm_path, sizeof(jvm_path));
    char *end = strrchr(jvm_path, *os::file_separator());
    if (end != nullptr) *end = '\0';
    size_t jvm_path_len = strlen(jvm_path);
    size_t file_sep_len = strlen(os::file_separator());
    const size_t len = jvm_path_len + file_sep_len + 20;
    _default_archive_path = NEW_C_HEAP_ARRAY(char, len, mtArguments);
    jio_snprintf(_default_archive_path, len,
                LP64_ONLY(!UseCompressedOops ? "%s%sclasses_nocoops.jsa":) "%s%sclasses.jsa",
                jvm_path, os::file_separator());
  }
  return _default_archive_path;
}

void CDSConfig::set_shared_spaces_flags_and_archive_paths() {
  if (CDSConfig::is_dumping_static_archive()) {
    if (RequireSharedSpaces) {
      warning("Cannot dump shared archive while using shared archive");
    }
    UseSharedSpaces = false;
  }
#if INCLUDE_CDS
  // Initialize shared archive paths which could include both base and dynamic archive paths
  // This must be after set_ergonomics_flags() called so flag UseCompressedOops is set properly.
  //
  // UseSharedSpaces may be disabled if -XX:SharedArchiveFile is invalid.
  if (CDSConfig::is_dumping_static_archive() || UseSharedSpaces) {
    CDSConfig::init_shared_archive_paths();
  }
#endif  // INCLUDE_CDS
}

int CDSConfig::num_archives(const char* archive_path) {
  if (archive_path == nullptr) {
    return 0;
  }
  int npaths = 1;
  char* p = (char*)archive_path;
  while (*p != '\0') {
    if (*p == os::path_separator()[0]) {
      npaths++;
    }
    p++;
  }
  return npaths;
}

void CDSConfig::extract_shared_archive_paths(const char* archive_path,
                                         char** base_archive_path,
                                         char** top_archive_path) {
  char* begin_ptr = (char*)archive_path;
  char* end_ptr = strchr((char*)archive_path, os::path_separator()[0]);
  if (end_ptr == nullptr || end_ptr == begin_ptr) {
    vm_exit_during_initialization("Base archive was not specified", archive_path);
  }
  size_t len = end_ptr - begin_ptr;
  char* cur_path = NEW_C_HEAP_ARRAY(char, len + 1, mtInternal);
  strncpy(cur_path, begin_ptr, len);
  cur_path[len] = '\0';
  *base_archive_path = cur_path;

  begin_ptr = ++end_ptr;
  if (*begin_ptr == '\0') {
    vm_exit_during_initialization("Top archive was not specified", archive_path);
  }
  end_ptr = strchr(begin_ptr, '\0');
  assert(end_ptr != nullptr, "sanity");
  len = end_ptr - begin_ptr;
  cur_path = NEW_C_HEAP_ARRAY(char, len + 1, mtInternal);
  strncpy(cur_path, begin_ptr, len + 1);
  *top_archive_path = cur_path;
}

void CDSConfig::init_shared_archive_paths() {
  if (ArchiveClassesAtExit != nullptr) {
    assert(!RecordDynamicDumpInfo, "already checked");
    if (CDSConfig::is_dumping_static_archive()) {
      vm_exit_during_initialization("-XX:ArchiveClassesAtExit cannot be used with -Xshare:dump");
    }
    Arguments::check_unsupported_dumping_properties();

    if (os::same_files(CDSConfig::get_default_archive_path(), ArchiveClassesAtExit)) {
      vm_exit_during_initialization(
        "Cannot specify the default CDS archive for -XX:ArchiveClassesAtExit", CDSConfig::get_default_archive_path());
    }
  }

  if (SharedArchiveFile == nullptr) {
    SharedArchivePath = CDSConfig::get_default_archive_path();
  } else {
    int archives = num_archives(SharedArchiveFile);
    assert(archives > 0, "must be");

    if (CDSConfig::is_dumping_archive() && archives > 1) {
      vm_exit_during_initialization(
        "Cannot have more than 1 archive file specified in -XX:SharedArchiveFile during CDS dumping");
    }

    if (CDSConfig::is_dumping_static_archive()) {
      assert(archives == 1, "must be");
      // Static dump is simple: only one archive is allowed in SharedArchiveFile. This file
      // will be overwritten no matter regardless of its contents
      SharedArchivePath = os::strdup_check_oom(SharedArchiveFile, mtArguments);
    } else {
      // SharedArchiveFile may specify one or two files. In case (c), the path for base.jsa
      // is read from top.jsa
      //    (a) 1 file:  -XX:SharedArchiveFile=base.jsa
      //    (b) 2 files: -XX:SharedArchiveFile=base.jsa:top.jsa
      //    (c) 2 files: -XX:SharedArchiveFile=top.jsa
      //
      // However, if either RecordDynamicDumpInfo or ArchiveClassesAtExit is used, we do not
      // allow cases (b) and (c). Case (b) is already checked above.

      if (archives > 2) {
        vm_exit_during_initialization(
          "Cannot have more than 2 archive files specified in the -XX:SharedArchiveFile option");
      }
      if (archives == 1) {
        char* base_archive_path = nullptr;
        bool success =
          FileMapInfo::get_base_archive_name_from_header(SharedArchiveFile, &base_archive_path);
        if (!success) {
          // If +AutoCreateSharedArchive and the specified shared archive does not exist,
          // regenerate the dynamic archive base on default archive.
          if (AutoCreateSharedArchive && !os::file_exists(SharedArchiveFile)) {
            CDSConfig::enable_dumping_dynamic_archive();
            ArchiveClassesAtExit = const_cast<char *>(SharedArchiveFile);
            SharedArchivePath = CDSConfig::get_default_archive_path();
            SharedArchiveFile = nullptr;
          } else {
            if (AutoCreateSharedArchive) {
              warning("-XX:+AutoCreateSharedArchive is unsupported when base CDS archive is not loaded. Run with -Xlog:cds for more info.");
              AutoCreateSharedArchive = false;
            }
            Arguments::no_shared_spaces("invalid archive");
          }
        } else if (base_archive_path == nullptr) {
          // User has specified a single archive, which is a static archive.
          SharedArchivePath = const_cast<char *>(SharedArchiveFile);
        } else {
          // User has specified a single archive, which is a dynamic archive.
          SharedDynamicArchivePath = const_cast<char *>(SharedArchiveFile);
          SharedArchivePath = base_archive_path; // has been c-heap allocated.
        }
      } else {
        extract_shared_archive_paths((const char*)SharedArchiveFile,
                                      &SharedArchivePath, &SharedDynamicArchivePath);
        if (SharedArchivePath == nullptr) {
          assert(SharedDynamicArchivePath == nullptr, "must be");
          Arguments::no_shared_spaces("invalid archive");
        }
      }

      if (SharedDynamicArchivePath != nullptr) {
        // Check for case (c)
        if (RecordDynamicDumpInfo) {
          vm_exit_during_initialization("-XX:+RecordDynamicDumpInfo is unsupported when a dynamic CDS archive is specified in -XX:SharedArchiveFile",
                                        SharedArchiveFile);
        }
        if (ArchiveClassesAtExit != nullptr) {
          vm_exit_during_initialization("-XX:ArchiveClassesAtExit is unsupported when a dynamic CDS archive is specified in -XX:SharedArchiveFile",
                                        SharedArchiveFile);
        }
      }

      if (ArchiveClassesAtExit != nullptr && os::same_files(SharedArchiveFile, ArchiveClassesAtExit)) {
          vm_exit_during_initialization(
            "Cannot have the same archive file specified for -XX:SharedArchiveFile and -XX:ArchiveClassesAtExit",
            SharedArchiveFile);
      }
    }
  }
}

#if INCLUDE_CDS_JAVA_HEAP
bool CDSConfig::is_dumping_heap() {
  // heap dump is not supported in dynamic dump
  return is_dumping_static_archive() && HeapShared::can_write();
}

bool CDSConfig::is_dumping_full_module_graph() {
  if (!_dumping_full_module_graph_disabled &&
      is_dumping_heap() &&
      MetaspaceShared::use_optimized_module_handling()) {
    return true;
  } else {
    return false;
  }
}

bool CDSConfig::is_loading_full_module_graph() {
  if (ClassLoaderDataShared::is_full_module_graph_loaded()) {
    return true;
  }

  if (!_loading_full_module_graph_disabled &&
      UseSharedSpaces &&
      ArchiveHeapLoader::can_use() &&
      MetaspaceShared::use_optimized_module_handling()) {
    // Classes used by the archived full module graph are loaded in JVMTI early phase.
    assert(!(JvmtiExport::should_post_class_file_load_hook() && JvmtiExport::has_early_class_hook_env()),
           "CDS should be disabled if early class hooks are enabled");
    return true;
  } else {
    return false;
  }
}

void CDSConfig::disable_dumping_full_module_graph(const char* reason) {
  if (!_dumping_full_module_graph_disabled) {
    _dumping_full_module_graph_disabled = true;
    if (reason != nullptr) {
      log_info(cds)("full module graph cannot be dumped: %s", reason);
    }
  }
}

void CDSConfig::disable_loading_full_module_graph(const char* reason) {
  assert(!ClassLoaderDataShared::is_full_module_graph_loaded(), "you call this function too late!");
  if (!_loading_full_module_graph_disabled) {
    _loading_full_module_graph_disabled = true;
    if (reason != nullptr) {
      log_info(cds)("full module graph cannot be loaded: %s", reason);
    }
  }
}
#endif // INCLUDE_CDS_JAVA_HEAP
