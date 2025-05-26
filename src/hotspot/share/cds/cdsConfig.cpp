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

#include "cds/aotLogging.hpp"
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
#include "prims/jvmtiAgentList.hpp"
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

const char* CDSConfig::_default_archive_path = nullptr;
const char* CDSConfig::_input_static_archive_path = nullptr;
const char* CDSConfig::_input_dynamic_archive_path = nullptr;
const char* CDSConfig::_output_archive_path = nullptr;

JavaThread* CDSConfig::_dumper_thread = nullptr;

int CDSConfig::get_status() {
  assert(Universe::is_fully_initialized(), "status is finalized only after Universe is initialized");
  return (is_dumping_archive()              ? IS_DUMPING_ARCHIVE : 0) |
         (is_dumping_method_handles()       ? IS_DUMPING_METHOD_HANDLES : 0) |
         (is_dumping_static_archive()       ? IS_DUMPING_STATIC_ARCHIVE : 0) |
         (is_logging_lambda_form_invokers() ? IS_LOGGING_LAMBDA_FORM_INVOKERS : 0) |
         (is_using_archive()                ? IS_USING_ARCHIVE : 0);
}

DEBUG_ONLY(static bool _cds_ergo_initialize_started = false);

void CDSConfig::ergo_initialize() {
  DEBUG_ONLY(_cds_ergo_initialize_started = true);

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
  if (is_dumping_static_archive() || is_using_archive()) {
    if (new_aot_flags_used()) {
      ergo_init_aot_paths();
    } else {
      ergo_init_classic_archive_paths();
    }
  }

  if (!is_dumping_heap()) {
    _is_dumping_full_module_graph = false;
  }
}

const char* CDSConfig::default_archive_path() {
  // The path depends on UseCompressedOops, etc, which are set by GC ergonomics just
  // before CDSConfig::ergo_initialize() is called.
  assert(_cds_ergo_initialize_started, "sanity");
  if (_default_archive_path == nullptr) {
    char jvm_path[JVM_MAXPATHLEN];
    os::jvm_path(jvm_path, sizeof(jvm_path));
    char *end = strrchr(jvm_path, *os::file_separator());
    if (end != nullptr) *end = '\0';
    stringStream tmp;
    tmp.print("%s%sclasses", jvm_path, os::file_separator());
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

int CDSConfig::num_archive_paths(const char* path_spec) {
  if (path_spec == nullptr) {
    return 0;
  }
  int npaths = 1;
  char* p = (char*)path_spec;
  while (*p != '\0') {
    if (*p == os::path_separator()[0]) {
      npaths++;
    }
    p++;
  }
  return npaths;
}

void CDSConfig::extract_archive_paths(const char* archive_path,
                                      const char** base_archive_path,
                                      const char** top_archive_path) {
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

void CDSConfig::ergo_init_classic_archive_paths() {
  assert(_cds_ergo_initialize_started, "sanity");
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
    _input_static_archive_path = default_archive_path();
    if (is_dumping_static_archive()) {
      _output_archive_path = _input_static_archive_path;
    }
  } else {
    int num_archives = num_archive_paths(SharedArchiveFile);
    assert(num_archives > 0, "must be");

    if (is_dumping_archive() && num_archives > 1) {
      vm_exit_during_initialization(
        "Cannot have more than 1 archive file specified in -XX:SharedArchiveFile during CDS dumping");
    }

    if (is_dumping_static_archive()) {
      assert(num_archives == 1, "just checked above");
      // Static dump is simple: only one archive is allowed in SharedArchiveFile. This file
      // will be overwritten regardless of its contents
      _output_archive_path = SharedArchiveFile;
    } else {
      // SharedArchiveFile may specify one or two files. In case (c), the path for base.jsa
      // is read from top.jsa
      //    (a) 1 file:  -XX:SharedArchiveFile=base.jsa
      //    (b) 2 files: -XX:SharedArchiveFile=base.jsa:top.jsa
      //    (c) 2 files: -XX:SharedArchiveFile=top.jsa
      //
      // However, if either RecordDynamicDumpInfo or ArchiveClassesAtExit is used, we do not
      // allow cases (b) and (c). Case (b) is already checked above.

      if (num_archives > 2) {
        vm_exit_during_initialization(
          "Cannot have more than 2 archive files specified in the -XX:SharedArchiveFile option");
      }

      if (num_archives == 1) {
        const char* base_archive_path = nullptr;
        bool success =
          FileMapInfo::get_base_archive_name_from_header(SharedArchiveFile, &base_archive_path);
        if (!success) {
          // If +AutoCreateSharedArchive and the specified shared archive does not exist,
          // regenerate the dynamic archive base on default archive.
          if (AutoCreateSharedArchive && !os::file_exists(SharedArchiveFile)) {
            enable_dumping_dynamic_archive(SharedArchiveFile);
            FLAG_SET_ERGO(ArchiveClassesAtExit, SharedArchiveFile);
            _input_static_archive_path = default_archive_path();
            FLAG_SET_ERGO(SharedArchiveFile, nullptr);
         } else {
            if (AutoCreateSharedArchive) {
              warning("-XX:+AutoCreateSharedArchive is unsupported when base CDS archive is not loaded. Run with -Xlog:cds for more info.");
              AutoCreateSharedArchive = false;
            }
            aot_log_error(aot)("Not a valid %s (%s)", type_of_archive_being_loaded(), SharedArchiveFile);
            Arguments::no_shared_spaces("invalid archive");
          }
        } else if (base_archive_path == nullptr) {
          // User has specified a single archive, which is a static archive.
          _input_static_archive_path = SharedArchiveFile;
        } else {
          // User has specified a single archive, which is a dynamic archive.
          _input_dynamic_archive_path = SharedArchiveFile;
          _input_static_archive_path = base_archive_path; // has been c-heap allocated.
        }
      } else {
        extract_archive_paths(SharedArchiveFile,
                              &_input_static_archive_path, &_input_dynamic_archive_path);
        if (_input_static_archive_path == nullptr) {
          assert(_input_dynamic_archive_path == nullptr, "must be");
          Arguments::no_shared_spaces("invalid archive");
        }
      }

      if (_input_dynamic_archive_path != nullptr) {
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
    aot_log_info(aot)("optimized module handling: disabled due to incompatible property: %s=%s", key, value);
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
      aot_log_info(aot)("full module graph: disabled due to incompatible property: %s=%s", key, value);
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
        aot_log_warning(aot)("AOT cache is disabled when the %s option is specified.", option);
      } else {
        aot_log_info(aot)("CDS is disabled when the %s option is specified.", option);
      }
    }
    return true;
  }
  return false;
}

#define CHECK_NEW_FLAG(f) check_new_flag(FLAG_IS_DEFAULT(f), #f)

void CDSConfig::check_new_flag(bool new_flag_is_default, const char* new_flag_name) {
  if (old_cds_flags_used() && !new_flag_is_default) {
    vm_exit_during_initialization(err_msg("Option %s cannot be used at the same time with "
                                          "-Xshare:on, -Xshare:auto, -Xshare:off, -Xshare:dump, "
                                          "DumpLoadedClassList, SharedClassListFile, or SharedArchiveFile",
                                          new_flag_name));
  }
}

#define CHECK_SINGLE_PATH(f) check_flag_single_path(#f, f)

void CDSConfig::check_flag_single_path(const char* flag_name, const char* value) {
  if (value != nullptr && num_archive_paths(value) != 1) {
    vm_exit_during_initialization(err_msg("Option %s must specify a single file name", flag_name));
  }
}

void CDSConfig::check_aot_flags() {
  if (!FLAG_IS_DEFAULT(DumpLoadedClassList) ||
      !FLAG_IS_DEFAULT(SharedClassListFile) ||
      !FLAG_IS_DEFAULT(SharedArchiveFile)) {
    _old_cds_flags_used = true;
  }

  // "New" AOT flags must not be mixed with "classic" flags such as -Xshare:dump
  CHECK_NEW_FLAG(AOTCache);
  CHECK_NEW_FLAG(AOTConfiguration);
  CHECK_NEW_FLAG(AOTMode);

  CHECK_SINGLE_PATH(AOTCache);
  CHECK_SINGLE_PATH(AOTConfiguration);

  if (FLAG_IS_DEFAULT(AOTCache) && AOTAdapterCaching) {
    log_debug(aot,codecache,init)("AOTCache is not specified - AOTAdapterCaching is ignored");
  }
  if (FLAG_IS_DEFAULT(AOTCache) && AOTStubCaching) {
    log_debug(aot,codecache,init)("AOTCache is not specified - AOTStubCaching is ignored");
  }

  if (FLAG_IS_DEFAULT(AOTCache) && FLAG_IS_DEFAULT(AOTConfiguration) && FLAG_IS_DEFAULT(AOTMode)) {
    // AOTCache/AOTConfiguration/AOTMode not used -> using the "classic CDS" workflow.
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

  // This is an old flag used by CDS regression testing only. It doesn't apply
  // to the AOT workflow.
  FLAG_SET_ERGO(AllowArchivingWithJavaAgent, false);
}

void CDSConfig::check_aotmode_off() {
  UseSharedSpaces = false;
  RequireSharedSpaces = false;
}

void CDSConfig::check_aotmode_auto_or_on() {
  if (!FLAG_IS_DEFAULT(AOTConfiguration)) {
    vm_exit_during_initialization("AOTConfiguration can only be used with -XX:AOTMode=record or -XX:AOTMode=create");
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

  _is_dumping_final_static_archive = true;
  UseSharedSpaces = true;
  RequireSharedSpaces = true;

  if (!FileMapInfo::is_preimage_static_archive(AOTConfiguration)) {
    vm_exit_during_initialization("Must be a valid AOT configuration generated by the current JVM", AOTConfiguration);
  }

  CDSConfig::enable_dumping_static_archive();

  // We don't load any agents in the assembly phase, so we can ensure that the agents
  // cannot affect the contents of the AOT cache. E.g., we don't want the agents to
  // redefine any cached classes. We also don't want the agents to modify heap objects that
  // are cached.
  //
  // Since application is not executed in the assembly phase, there's no need to load
  // the agents anyway -- no one will notice that the agents are not loaded.
  JvmtiAgentList::disable_agent_list();
}

void CDSConfig::ergo_init_aot_paths() {
  assert(_cds_ergo_initialize_started, "sanity");
  if (is_dumping_static_archive()) {
    if (is_dumping_preimage_static_archive()) {
      _output_archive_path = AOTConfiguration;
    } else {
      assert(is_dumping_final_static_archive(), "must be");
      _input_static_archive_path = AOTConfiguration;
      _output_archive_path = AOTCache;
    }
  } else if (is_using_archive()) {
    if (FLAG_IS_DEFAULT(AOTCache)) {
      // Only -XX:AOTMode={auto,on} is specified
      _input_static_archive_path = default_archive_path();
    } else {
      _input_static_archive_path = AOTCache;
    }
  }
}

bool CDSConfig::check_vm_args_consistency(bool patch_mod_javabase, bool mode_flag_cmd_line) {
  assert(!_cds_ergo_initialize_started, "This is called earlier than CDSConfig::ergo_initialize()");

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
    if (is_dumping_preimage_static_archive() || is_dumping_final_static_archive()) {
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
      aot_log_info(aot)("reduced -Xcomp to -Xmixed for static dumping");
      Arguments::set_mode_flags(Arguments::_mixed);
    }

    // String deduplication may cause CDS to iterate the strings in different order from one
    // run to another which resulting in non-determinstic CDS archives.
    // Disable UseStringDeduplication while dumping CDS archive.
    UseStringDeduplication = false;
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
    enable_dumping_dynamic_archive(ArchiveClassesAtExit);
  }

  if (AutoCreateSharedArchive) {
    if (SharedArchiveFile == nullptr) {
      aot_log_warning(aot)("-XX:+AutoCreateSharedArchive requires -XX:SharedArchiveFile");
      return false;
    }
    if (ArchiveClassesAtExit != nullptr) {
      aot_log_warning(aot)("-XX:+AutoCreateSharedArchive does not work with ArchiveClassesAtExit");
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
      aot_log_info(aot)("All non-system classes will be verified (-Xverify:remote) during CDS dump time.");
    }
  }

  return true;
}

void CDSConfig::prepare_for_dumping() {
  assert(CDSConfig::is_dumping_archive(), "sanity");

  if (is_dumping_dynamic_archive() && !is_using_archive()) {
    assert(!is_dumping_static_archive(), "cannot be dumping both static and dynamic archives");

    // This could happen if SharedArchiveFile has failed to load:
    // - -Xshare:off was specified
    // - SharedArchiveFile points to an non-existent file.
    // - SharedArchiveFile points to an archive that has failed CRC check
    // - SharedArchiveFile is not specified and the VM doesn't have a compatible default archive

#define __THEMSG " is unsupported when base CDS archive is not loaded. Run with -Xlog:cds for more info."
    if (RecordDynamicDumpInfo) {
      aot_log_error(aot)("-XX:+RecordDynamicDumpInfo%s", __THEMSG);
      MetaspaceShared::unrecoverable_loading_error();
    } else {
      assert(ArchiveClassesAtExit != nullptr, "sanity");
      aot_log_warning(aot)("-XX:ArchiveClassesAtExit" __THEMSG);
    }
#undef __THEMSG
    disable_dumping_dynamic_archive();
    return;
  }

  check_unsupported_dumping_module_options();
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

void CDSConfig::enable_dumping_dynamic_archive(const char* output_path) {
  _is_dumping_dynamic_archive = true;
  if (output_path == nullptr) {
    // output_path can be null when the VM is started with -XX:+RecordDynamicDumpInfo
    // in anticipation of "jcmd VM.cds dynamic_dump", which will provide the actual
    // output path.
    _output_archive_path = nullptr;
  } else {
    _output_archive_path = os::strdup_check_oom(output_path, mtArguments);
  }
}

bool CDSConfig::allow_only_single_java_thread() {
  // See comments in JVM_StartThread()
  return is_dumping_classic_static_archive() || is_dumping_final_static_archive();
}

bool CDSConfig::is_using_archive() {
  return UseSharedSpaces;
}

bool CDSConfig::is_using_only_default_archive() {
  return is_using_archive() &&
         input_static_archive_path() != nullptr &&
         default_archive_path() != nullptr &&
         strcmp(input_static_archive_path(), default_archive_path()) == 0 &&
         input_dynamic_archive_path() == nullptr;
}

bool CDSConfig::is_logging_lambda_form_invokers() {
  return ClassListWriter::is_enabled() || is_dumping_dynamic_archive();
}

bool CDSConfig::is_dumping_regenerated_lambdaform_invokers() {
  if (is_dumping_final_static_archive()) {
    // No need to regenerate -- the lambda form invokers should have been regenerated
    // in the preimage archive (if allowed)
    return false;
  } else if (is_dumping_dynamic_archive() && is_using_aot_linked_classes()) {
    // The base archive has aot-linked classes that may have AOT-resolved CP references
    // that point to the lambda form invokers in the base archive. Such pointers will
    // be invalid if lambda form invokers are regenerated in the dynamic archive.
    return false;
  } else if (CDSConfig::is_dumping_method_handles()) {
    // Work around JDK-8310831, as some methods in lambda form holder classes may not get generated.
    return false;
  } else {
    return is_dumping_archive();
  }
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
  aot_log_info(aot)("Archived java heap is not supported: %s", reason);
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
      aot_log_info(aot)("full module graph cannot be dumped: %s", reason);
    }
  }
}

void CDSConfig::stop_using_full_module_graph(const char* reason) {
  assert(!ClassLoaderDataShared::is_full_module_graph_loaded(), "you call this function too late!");
  if (_is_using_full_module_graph) {
    _is_using_full_module_graph = false;
    if (reason != nullptr) {
      aot_log_info(aot)("full module graph cannot be loaded: %s", reason);
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

// AOT code generation and its archiving is disabled by default.
// We enable it only in the final image dump after the metadata and heap are dumped.
// This affects only JITed code because it may have embedded oops and metadata pointers
// which AOT code encodes as offsets in final CDS archive regions.

static bool _is_dumping_aot_code = false;

bool CDSConfig::is_dumping_aot_code() {
  return _is_dumping_aot_code;
}

void CDSConfig::disable_dumping_aot_code() {
  _is_dumping_aot_code = false;
}

void CDSConfig::enable_dumping_aot_code() {
  _is_dumping_aot_code = true;
}

bool CDSConfig::is_dumping_adapters() {
  return (AOTAdapterCaching && is_dumping_final_static_archive());
}
