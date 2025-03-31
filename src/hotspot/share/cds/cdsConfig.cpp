/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/archiveHeapLoader.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/classListWriter.hpp"
#include "cds/filemap.hpp"
#include "cds/heapShared.hpp"
#include "classfile/classLoaderDataShared.hpp"
#include "classfile/moduleEntry.hpp"
#include "include/jvm_io.h"
#include "logging/log.hpp"
#include "memory/universe.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/formatBuffer.hpp"

bool CDSConfig::_is_dumping_static_archive = false;
bool CDSConfig::_is_dumping_preimage_static_archive = false;
bool CDSConfig::_is_dumping_final_static_archive = false;
bool CDSConfig::_is_dumping_dynamic_archive = false;
bool CDSConfig::_is_using_optimized_module_handling = true;
bool CDSConfig::_is_dumping_full_module_graph = true;
bool CDSConfig::_is_using_full_module_graph = true;
bool CDSConfig::_has_aot_linked_classes = false;
bool CDSConfig::_old_cds_flags_used = false;
bool CDSConfig::_new_aot_flags_used = false;
bool CDSConfig::_disable_heap_dumping = false;

char* CDSConfig::_default_archive_path = nullptr;
char* CDSConfig::_static_archive_path = nullptr;
char* CDSConfig::_dynamic_archive_path = nullptr;

JavaThread* CDSConfig::_dumper_thread = nullptr;

int CDSConfig::get_status() {
  assert(Universe::is_fully_initialized(), "status is finalized only after Universe is initialized");
  return (is_dumping_archive()              ? IS_DUMPING_ARCHIVE : 0) |
         (is_dumping_method_handles()       ? IS_DUMPING_METHOD_HANDLES : 0) |
         (is_dumping_static_archive()       ? IS_DUMPING_STATIC_ARCHIVE : 0) |
         (is_logging_lambda_form_invokers() ? IS_LOGGING_LAMBDA_FORM_INVOKERS : 0) |
         (is_using_archive()                ? IS_USING_ARCHIVE : 0);
}

void CDSConfig::initialize() {
  if (is_dumping_static_archive() && !is_dumping_final_static_archive()) {
    // Note: -Xshare and -XX:AOTMode flags are mutually exclusive.
    // - Classic workflow: -Xshare:on and -Xshare:dump cannot take effect at the same time.
    // - JEP 483 workflow: -XX:AOTMode:record and -XX:AOTMode=on cannot take effect at the same time.
    // So we can never come to here with RequireSharedSpaces==true.
    assert(!RequireSharedSpaces, "sanity");

    // If dumping the classic archive, or making an AOT training run (dumping a preimage archive),
    // for sanity, parse all classes from classfiles.
    // TODO: in the future, if we want to support re-training on top of an existing AOT cache, this
    // needs to be changed.
    UseSharedSpaces = false;
  }

  // Initialize shared archive paths which could include both base and dynamic archive paths
  // This must be after set_ergonomics_flags() called so flag UseCompressedOops is set properly.
  //
  // UseSharedSpaces may be disabled if -XX:SharedArchiveFile is invalid.
  if (is_dumping_static_archive() || is_using_archive()) {
    init_shared_archive_paths();
  }

  if (!is_dumping_heap()) {
    _is_dumping_full_module_graph = false;
  }
}

char* CDSConfig::default_archive_path() {
  if (_default_archive_path == nullptr) {
    stringStream tmp;
    const char* subdir = WINDOWS_ONLY("bin") NOT_WINDOWS("lib");
    tmp.print("%s%s%s%s%s%sclasses", Arguments::get_java_home(), os::file_separator(), subdir,
              os::file_separator(), Abstract_VM_Version::vm_variant(), os::file_separator());
#ifdef _LP64
    if (!UseCompressedOops) {
      tmp.print_raw("_nocoops");
    }
    if (UseCompactObjectHeaders) {
      // Note that generation of xxx_coh.jsa variants require
      // --enable-cds-archive-coh at build time
      tmp.print_raw("_coh");
    }
#endif
    tmp.print_raw(".jsa");
    _default_archive_path = os::strdup(tmp.base());
  }
  return _default_archive_path;
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
    if (is_dumping_static_archive()) {
      vm_exit_during_initialization("-XX:ArchiveClassesAtExit cannot be used with -Xshare:dump");
    }
    check_unsupported_dumping_module_options();

    if (os::same_files(default_archive_path(), ArchiveClassesAtExit)) {
      vm_exit_during_initialization(
        "Cannot specify the default CDS archive for -XX:ArchiveClassesAtExit", default_archive_path());
    }
  }

  if (SharedArchiveFile == nullptr) {
    _static_archive_path = default_archive_path();
  } else {
    int archives = num_archives(SharedArchiveFile);
    assert(archives > 0, "must be");

    if (is_dumping_archive() && archives > 1) {
      vm_exit_during_initialization(
        "Cannot have more than 1 archive file specified in -XX:SharedArchiveFile during CDS dumping");
    }

    if (is_dumping_static_archive()) {
      assert(archives == 1, "must be");
      // Static dump is simple: only one archive is allowed in SharedArchiveFile. This file
      // will be overwritten no matter regardless of its contents
      _static_archive_path = os::strdup_check_oom(SharedArchiveFile, mtArguments);
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
            enable_dumping_dynamic_archive();
            ArchiveClassesAtExit = const_cast<char *>(SharedArchiveFile);
            _static_archive_path = default_archive_path();
            SharedArchiveFile = nullptr;
          } else {
            if (AutoCreateSharedArchive) {
              warning("-XX:+AutoCreateSharedArchive is unsupported when base CDS archive is not loaded. Run with -Xlog:cds for more info.");
              AutoCreateSharedArchive = false;
            }
            log_error(cds)("Not a valid %s (%s)", new_aot_flags_used() ? "AOT cache" : "archive", SharedArchiveFile);
            Arguments::no_shared_spaces("invalid archive");
          }
        } else if (base_archive_path == nullptr) {
          // User has specified a single archive, which is a static archive.
          _static_archive_path = const_cast<char *>(SharedArchiveFile);
        } else {
          // User has specified a single archive, which is a dynamic archive.
          _dynamic_archive_path = const_cast<char *>(SharedArchiveFile);
          _static_archive_path = base_archive_path; // has been c-heap allocated.
        }
      } else {
        extract_shared_archive_paths((const char*)SharedArchiveFile,
                                      &_static_archive_path, &_dynamic_archive_path);
        if (_static_archive_path == nullptr) {
          assert(_dynamic_archive_path == nullptr, "must be");
          Arguments::no_shared_spaces("invalid archive");
        }
      }

      if (_dynamic_archive_path != nullptr) {
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

void CDSConfig::check_internal_module_property(const char* key, const char* value) {
  if (Arguments::is_incompatible_cds_internal_module_property(key)) {
    stop_using_optimized_module_handling();
    log_info(cds)("optimized module handling: disabled due to incompatible property: %s=%s", key, value);
  }
}

void CDSConfig::check_incompatible_property(const char* key, const char* value) {
  static const char* incompatible_properties[] = {
    "java.system.class.loader",
    "jdk.module.showModuleResolution",
    "jdk.module.validation"
  };

  for (const char* property : incompatible_properties) {
    if (strcmp(key, property) == 0) {
      stop_dumping_full_module_graph();
      stop_using_full_module_graph();
      log_info(cds)("full module graph: disabled due to incompatible property: %s=%s", key, value);
      break;
    }
  }

}

// Returns any JVM command-line option, such as "--patch-module", that's not supported by CDS.
static const char* find_any_unsupported_module_option() {
  // Note that arguments.cpp has translated the command-line options into properties. If we find an
  // unsupported property, translate it back to its command-line option for better error reporting.

  // The following properties are checked by Arguments::is_internal_module_property() and cannot be
  // directly specified in the command-line.
  static const char* unsupported_module_properties[] = {
    "jdk.module.limitmods",
    "jdk.module.upgrade.path",
    "jdk.module.patch.0"
  };
  static const char* unsupported_module_options[] = {
    "--limit-modules",
    "--upgrade-module-path",
    "--patch-module"
  };

  assert(ARRAY_SIZE(unsupported_module_properties) == ARRAY_SIZE(unsupported_module_options), "must be");
  SystemProperty* sp = Arguments::system_properties();
  while (sp != nullptr) {
    for (uint i = 0; i < ARRAY_SIZE(unsupported_module_properties); i++) {
      if (strcmp(sp->key(), unsupported_module_properties[i]) == 0) {
        return unsupported_module_options[i];
      }
    }
    sp = sp->next();
  }

  return nullptr; // not found
}

void CDSConfig::check_unsupported_dumping_module_options() {
  assert(is_dumping_archive(), "this function is only used with CDS dump time");
  const char* option = find_any_unsupported_module_option();
  if (option != nullptr) {
    vm_exit_during_initialization("Cannot use the following option when dumping the shared archive", option);
  }
  // Check for an exploded module build in use with -Xshare:dump.
  if (!Arguments::has_jimage()) {
    vm_exit_during_initialization("Dumping the shared archive is not supported with an exploded module build");
  }
}

bool CDSConfig::has_unsupported_runtime_module_options() {
  assert(is_using_archive(), "this function is only used with -Xshare:{on,auto}");
  if (ArchiveClassesAtExit != nullptr) {
    // dynamic dumping, just return false for now.
    // check_unsupported_dumping_properties() will be called later to check the same set of
    // properties, and will exit the VM with the correct error message if the unsupported properties
    // are used.
    return false;
  }
  const char* option = find_any_unsupported_module_option();
  if (option != nullptr) {
    if (RequireSharedSpaces) {
      warning("CDS is disabled when the %s option is specified.", option);
    } else {
      if (new_aot_flags_used()) {
        log_warning(cds)("AOT cache is disabled when the %s option is specified.", option);
      } else {
        log_info(cds)("CDS is disabled when the %s option is specified.", option);
      }
    }
    return true;
  }
  return false;
}

#define CHECK_ALIAS(f) check_flag_alias(FLAG_IS_DEFAULT(f), #f)

void CDSConfig::check_flag_alias(bool alias_is_default, const char* alias_name) {
  if (old_cds_flags_used() && !alias_is_default) {
    vm_exit_during_initialization(err_msg("Option %s cannot be used at the same time with "
                                          "-Xshare:on, -Xshare:auto, -Xshare:off, -Xshare:dump, "
                                          "DumpLoadedClassList, SharedClassListFile, or SharedArchiveFile",
                                          alias_name));
  }
}

void CDSConfig::check_aot_flags() {
  if (!FLAG_IS_DEFAULT(DumpLoadedClassList) ||
      !FLAG_IS_DEFAULT(SharedClassListFile) ||
      !FLAG_IS_DEFAULT(SharedArchiveFile)) {
    _old_cds_flags_used = true;
  }

  CHECK_ALIAS(AOTCache);
  CHECK_ALIAS(AOTConfiguration);
  CHECK_ALIAS(AOTMode);

  if (FLAG_IS_DEFAULT(AOTCache) && FLAG_IS_DEFAULT(AOTConfiguration) && FLAG_IS_DEFAULT(AOTMode)) {
    // AOTCache/AOTConfiguration/AOTMode not used.
    return;
  } else {
    _new_aot_flags_used = true;
  }

  if (FLAG_IS_DEFAULT(AOTMode) || strcmp(AOTMode, "auto") == 0 || strcmp(AOTMode, "on") == 0) {
    check_aotmode_auto_or_on();
  } else if (strcmp(AOTMode, "off") == 0) {
    check_aotmode_off();
  } else {
    // AOTMode is record or create
    if (FLAG_IS_DEFAULT(AOTConfiguration)) {
      vm_exit_during_initialization(err_msg("-XX:AOTMode=%s cannot be used without setting AOTConfiguration", AOTMode));
    }

    if (strcmp(AOTMode, "record") == 0) {
      check_aotmode_record();
    } else {
      assert(strcmp(AOTMode, "create") == 0, "checked by AOTModeConstraintFunc");
      check_aotmode_create();
    }
  }
}

void CDSConfig::check_aotmode_off() {
  UseSharedSpaces = false;
  RequireSharedSpaces = false;
}

void CDSConfig::check_aotmode_auto_or_on() {
  if (!FLAG_IS_DEFAULT(AOTConfiguration)) {
    vm_exit_during_initialization("AOTConfiguration can only be used with -XX:AOTMode=record or -XX:AOTMode=create");
  }

  if (!FLAG_IS_DEFAULT(AOTCache)) {
    assert(FLAG_IS_DEFAULT(SharedArchiveFile), "already checked");
    FLAG_SET_ERGO(SharedArchiveFile, AOTCache);
  }

  UseSharedSpaces = true;
  if (FLAG_IS_DEFAULT(AOTMode) || (strcmp(AOTMode, "auto") == 0)) {
    RequireSharedSpaces = false;
  } else {
    assert(strcmp(AOTMode, "on") == 0, "already checked");
    RequireSharedSpaces = true;
  }
}

void CDSConfig::check_aotmode_record() {
  if (!FLAG_IS_DEFAULT(AOTCache)) {
    vm_exit_during_initialization("AOTCache must not be specified when using -XX:AOTMode=record");
  }

  assert(FLAG_IS_DEFAULT(DumpLoadedClassList), "already checked");
  assert(FLAG_IS_DEFAULT(SharedArchiveFile), "already checked");
  FLAG_SET_ERGO(SharedArchiveFile, AOTConfiguration);
  FLAG_SET_ERGO(DumpLoadedClassList, nullptr);
  UseSharedSpaces = false;
  RequireSharedSpaces = false;
  _is_dumping_static_archive = true;
  _is_dumping_preimage_static_archive = true;

  // At VM exit, the module graph may be contaminated with program states.
  // We will rebuild the module graph when dumping the CDS final image.
  disable_heap_dumping();
}

void CDSConfig::check_aotmode_create() {
  if (FLAG_IS_DEFAULT(AOTCache)) {
    vm_exit_during_initialization("AOTCache must be specified when using -XX:AOTMode=create");
  }

  assert(FLAG_IS_DEFAULT(SharedArchiveFile), "already checked");

  _is_dumping_final_static_archive = true;
  FLAG_SET_ERGO(SharedArchiveFile, AOTConfiguration);
  UseSharedSpaces = true;
  RequireSharedSpaces = true;

  if (!FileMapInfo::is_preimage_static_archive(AOTConfiguration)) {
    vm_exit_during_initialization("Must be a valid AOT configuration generated by the current JVM", AOTConfiguration);
  }

  CDSConfig::enable_dumping_static_archive();
}

bool CDSConfig::check_vm_args_consistency(bool patch_mod_javabase, bool mode_flag_cmd_line) {
  check_aot_flags();

  if (!FLAG_IS_DEFAULT(AOTMode)) {
    // Using any form of the new AOTMode switch enables enhanced optimizations.
    FLAG_SET_ERGO_IF_DEFAULT(AOTClassLinking, true);
  }

  if (AOTClassLinking) {
    // If AOTClassLinking is specified, enable all AOT optimizations by default.
    FLAG_SET_ERGO_IF_DEFAULT(AOTInvokeDynamicLinking, true);
  } else {
    // AOTInvokeDynamicLinking depends on AOTClassLinking.
    FLAG_SET_ERGO(AOTInvokeDynamicLinking, false);
  }

  if (is_dumping_static_archive()) {
    if (is_dumping_preimage_static_archive()) {
      // Don't tweak execution mode
    } else if (!mode_flag_cmd_line) {
      // By default, -Xshare:dump runs in interpreter-only mode, which is required for deterministic archive.
      //
      // If your classlist is large and you don't care about deterministic dumping, you can use
      // -Xshare:dump -Xmixed to improve dumping speed.
      Arguments::set_mode_flags(Arguments::_int);
    } else if (Arguments::mode() == Arguments::_comp) {
      // -Xcomp may use excessive CPU for the test tiers. Also, -Xshare:dump runs a small and fixed set of
      // Java code, so there's not much benefit in running -Xcomp.
      log_info(cds)("reduced -Xcomp to -Xmixed for static dumping");
      Arguments::set_mode_flags(Arguments::_mixed);
    }

    // String deduplication may cause CDS to iterate the strings in different order from one
    // run to another which resulting in non-determinstic CDS archives.
    // Disable UseStringDeduplication while dumping CDS archive.
    UseStringDeduplication = false;

    // Don't use SoftReferences so that objects used by java.lang.invoke tables can be archived.
    Arguments::PropertyList_add(new SystemProperty("java.lang.invoke.MethodHandleNatives.USE_SOFT_CACHE", "false", false));
  }

  // RecordDynamicDumpInfo is not compatible with ArchiveClassesAtExit
  if (ArchiveClassesAtExit != nullptr && RecordDynamicDumpInfo) {
    jio_fprintf(defaultStream::output_stream(),
                "-XX:+RecordDynamicDumpInfo cannot be used with -XX:ArchiveClassesAtExit.\n");
    return false;
  }

  if (ArchiveClassesAtExit == nullptr && !RecordDynamicDumpInfo) {
    disable_dumping_dynamic_archive();
  } else {
    enable_dumping_dynamic_archive();
  }

  if (AutoCreateSharedArchive) {
    if (SharedArchiveFile == nullptr) {
      log_warning(cds)("-XX:+AutoCreateSharedArchive requires -XX:SharedArchiveFile");
      return false;
    }
    if (ArchiveClassesAtExit != nullptr) {
      log_warning(cds)("-XX:+AutoCreateSharedArchive does not work with ArchiveClassesAtExit");
      return false;
    }
  }

  if (is_using_archive() && patch_mod_javabase) {
    Arguments::no_shared_spaces("CDS is disabled when " JAVA_BASE_NAME " module is patched.");
  }
  if (is_using_archive() && has_unsupported_runtime_module_options()) {
    UseSharedSpaces = false;
  }

  if (is_dumping_archive()) {
    // Always verify non-system classes during CDS dump
    if (!BytecodeVerificationRemote) {
      BytecodeVerificationRemote = true;
      log_info(cds)("All non-system classes will be verified (-Xverify:remote) during CDS dump time.");
    }
  }

  return true;
}

bool CDSConfig::is_dumping_classic_static_archive() {
  return _is_dumping_static_archive &&
    !is_dumping_preimage_static_archive() &&
    !is_dumping_final_static_archive();
}

bool CDSConfig::is_dumping_preimage_static_archive() {
  return _is_dumping_preimage_static_archive;
}

bool CDSConfig::is_dumping_final_static_archive() {
  return _is_dumping_final_static_archive;
}

bool CDSConfig::allow_only_single_java_thread() {
  // See comments in JVM_StartThread()
  return is_dumping_classic_static_archive() || is_dumping_final_static_archive();
}

bool CDSConfig::is_using_archive() {
  return UseSharedSpaces;
}

bool CDSConfig::is_logging_lambda_form_invokers() {
  return ClassListWriter::is_enabled() || is_dumping_dynamic_archive();
}

void CDSConfig::stop_using_optimized_module_handling() {
  _is_using_optimized_module_handling = false;
  _is_dumping_full_module_graph = false; // This requires is_using_optimized_module_handling()
  _is_using_full_module_graph = false; // This requires is_using_optimized_module_handling()
}


CDSConfig::DumperThreadMark::DumperThreadMark(JavaThread* current) {
  assert(_dumper_thread == nullptr, "sanity");
  _dumper_thread = current;
}

CDSConfig::DumperThreadMark::~DumperThreadMark() {
  assert(_dumper_thread != nullptr, "sanity");
  _dumper_thread = nullptr;
}

bool CDSConfig::current_thread_is_vm_or_dumper() {
  Thread* t = Thread::current();
  return t != nullptr && (t->is_VM_thread() || t == _dumper_thread);
}

const char* CDSConfig::type_of_archive_being_loaded() {
  if (is_dumping_final_static_archive()) {
    return "AOT configuration file";
  } else if (new_aot_flags_used()) {
    return "AOT cache";
  } else {
    return "shared archive file";
  }
}

const char* CDSConfig::type_of_archive_being_written() {
  if (is_dumping_preimage_static_archive()) {
    return "AOT configuration file";
  } else if (new_aot_flags_used()) {
    return "AOT cache";
  } else {
    return "shared archive file";
  }
}

// If an incompatible VM options is found, return a text message that explains why
static const char* check_options_incompatible_with_dumping_heap() {
#if INCLUDE_CDS_JAVA_HEAP
  if (!UseCompressedClassPointers) {
    return "UseCompressedClassPointers must be true";
  }

  // Almost all GCs support heap region dump, except ZGC (so far).
  if (UseZGC) {
    return "ZGC is not supported";
  }

  return nullptr;
#else
  return "JVM not configured for writing Java heap objects";
#endif
}

void CDSConfig::log_reasons_for_not_dumping_heap() {
  const char* reason;

  assert(!is_dumping_heap(), "sanity");

  if (_disable_heap_dumping) {
    reason = "Programmatically disabled";
  } else {
    reason = check_options_incompatible_with_dumping_heap();
  }

  assert(reason != nullptr, "sanity");
  log_info(cds)("Archived java heap is not supported: %s", reason);
}

// This is *Legacy* optimization for lambdas before JEP 483. May be removed in the future.
bool CDSConfig::is_dumping_lambdas_in_legacy_mode() {
  return !is_dumping_method_handles();
}

#if INCLUDE_CDS_JAVA_HEAP
bool CDSConfig::are_vm_options_incompatible_with_dumping_heap() {
  return check_options_incompatible_with_dumping_heap() != nullptr;
}

bool CDSConfig::is_dumping_heap() {
  if (!(is_dumping_classic_static_archive() || is_dumping_final_static_archive())
      || are_vm_options_incompatible_with_dumping_heap()
      || _disable_heap_dumping) {
    return false;
  }
  return true;
}

bool CDSConfig::is_loading_heap() {
  return ArchiveHeapLoader::is_in_use();
}

bool CDSConfig::is_using_full_module_graph() {
  if (ClassLoaderDataShared::is_full_module_graph_loaded()) {
    return true;
  }

  if (!_is_using_full_module_graph) {
    return false;
  }

  if (is_using_archive() && ArchiveHeapLoader::can_use()) {
    // Classes used by the archived full module graph are loaded in JVMTI early phase.
    assert(!(JvmtiExport::should_post_class_file_load_hook() && JvmtiExport::has_early_class_hook_env()),
           "CDS should be disabled if early class hooks are enabled");
    return true;
  } else {
    _is_using_full_module_graph = false;
    return false;
  }
}

void CDSConfig::stop_dumping_full_module_graph(const char* reason) {
  if (_is_dumping_full_module_graph) {
    _is_dumping_full_module_graph = false;
    if (reason != nullptr) {
      log_info(cds)("full module graph cannot be dumped: %s", reason);
    }
  }
}

void CDSConfig::stop_using_full_module_graph(const char* reason) {
  assert(!ClassLoaderDataShared::is_full_module_graph_loaded(), "you call this function too late!");
  if (_is_using_full_module_graph) {
    _is_using_full_module_graph = false;
    if (reason != nullptr) {
      log_info(cds)("full module graph cannot be loaded: %s", reason);
    }
  }
}

bool CDSConfig::is_dumping_aot_linked_classes() {
  if (is_dumping_preimage_static_archive()) {
    return false;
  } else if (is_dumping_dynamic_archive()) {
    return is_using_full_module_graph() && AOTClassLinking;
  } else if (is_dumping_static_archive()) {
    return is_dumping_full_module_graph() && AOTClassLinking;
  } else {
    return false;
  }
}

bool CDSConfig::is_using_aot_linked_classes() {
  // Make sure we have the exact same module graph as in the assembly phase, or else
  // some aot-linked classes may not be visible so cannot be loaded.
  return is_using_full_module_graph() && _has_aot_linked_classes;
}

void CDSConfig::set_has_aot_linked_classes(bool has_aot_linked_classes) {
  _has_aot_linked_classes |= has_aot_linked_classes;
}

bool CDSConfig::is_initing_classes_at_dump_time() {
  return is_dumping_heap() && is_dumping_aot_linked_classes();
}

bool CDSConfig::is_dumping_invokedynamic() {
  // Requires is_dumping_aot_linked_classes(). Otherwise the classes of some archived heap
  // objects used by the archive indy callsites may be replaced at runtime.
  return AOTInvokeDynamicLinking && is_dumping_aot_linked_classes() && is_dumping_heap();
}

// When we are dumping aot-linked classes and we are able to write archived heap objects, we automatically
// enable the archiving of MethodHandles. This will in turn enable the archiving of MethodTypes and hidden
// classes that are used in the implementation of MethodHandles.
// Archived MethodHandles are required for higher-level optimizations such as AOT resolution of invokedynamic
// and dynamic proxies.
bool CDSConfig::is_dumping_method_handles() {
  return is_initing_classes_at_dump_time();
}

#endif // INCLUDE_CDS_JAVA_HEAP
