/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_CDSCONFIG_HPP
#define SHARE_CDS_CDSCONFIG_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class JavaThread;
class InstanceKlass;

class CDSConfig : public AllStatic {
#if INCLUDE_CDS
  static bool _is_dumping_static_archive;
  static bool _is_dumping_preimage_static_archive;
  static bool _is_dumping_final_static_archive;
  static bool _is_dumping_dynamic_archive;
  static bool _is_using_optimized_module_handling;
  static bool _is_dumping_full_module_graph;
  static bool _is_using_full_module_graph;
  static bool _has_aot_linked_classes;
  static bool _is_single_command_training;
  static bool _has_temp_aot_config_file;
  static bool _is_at_aot_safepoint;

  const static char* _default_archive_path;
  const static char* _input_static_archive_path;
  const static char* _input_dynamic_archive_path;
  const static char* _output_archive_path;

  static bool  _old_cds_flags_used;
  static bool  _new_aot_flags_used;
  static bool  _disable_heap_dumping;

  static JavaThread* _dumper_thread;
#endif

  static void extract_archive_paths(const char* archive_path,
                                    const char** base_archive_path,
                                    const char** top_archive_path);
  static int num_archive_paths(const char* path_spec);
  static void check_flag_single_path(const char* flag_name, const char* value);

  // Checks before Arguments::apply_ergo()
  static void check_new_flag(bool new_flag_is_default, const char* new_flag_name);
  static void check_aot_flags();
  static void check_aotmode_off();
  static void check_aotmode_auto_or_on();
  static void check_aotmode_record();
  static void check_aotmode_create();
  static void setup_compiler_args();
  static void check_unsupported_dumping_module_options();

  // Called after Arguments::apply_ergo() has started
  static void ergo_init_classic_archive_paths();
  static void ergo_init_aot_paths();

public:
  // Used by jdk.internal.misc.CDS.getCDSConfigStatus();
  static const int IS_DUMPING_AOT_LINKED_CLASSES   = 1 << 0;
  static const int IS_DUMPING_ARCHIVE              = 1 << 1;
  static const int IS_DUMPING_METHOD_HANDLES       = 1 << 2;
  static const int IS_DUMPING_STATIC_ARCHIVE       = 1 << 3;
  static const int IS_LOGGING_LAMBDA_FORM_INVOKERS = 1 << 4;
  static const int IS_USING_ARCHIVE                = 1 << 5;

  static int get_status() NOT_CDS_RETURN_(0);

  // Initialization and command-line checking
  static void ergo_initialize() NOT_CDS_RETURN;
  static void set_old_cds_flags_used()                       { CDS_ONLY(_old_cds_flags_used = true); }
  static bool old_cds_flags_used()                           { return CDS_ONLY(_old_cds_flags_used) NOT_CDS(false); }
  static bool new_aot_flags_used()                           { return CDS_ONLY(_new_aot_flags_used) NOT_CDS(false); }
  static void check_internal_module_property(const char* key, const char* value) NOT_CDS_RETURN;
  static void check_incompatible_property(const char* key, const char* value) NOT_CDS_RETURN;
  static bool has_unsupported_runtime_module_options() NOT_CDS_RETURN_(false);
  static bool check_vm_args_consistency(bool patch_mod_javabase, bool mode_flag_cmd_line) NOT_CDS_RETURN_(true);
  static const char* type_of_archive_being_loaded();
  static const char* type_of_archive_being_written();
  static void prepare_for_dumping();

  static bool is_at_aot_safepoint()                          { return CDS_ONLY(_is_at_aot_safepoint) NOT_CDS(false); }
  static void set_is_at_aot_safepoint(bool value)            { CDS_ONLY(_is_at_aot_safepoint = value); }

  // --- Basic CDS features

  // archive(s) in general
  static bool is_dumping_archive()                           { return is_dumping_static_archive() || is_dumping_dynamic_archive(); }

  // input archive(s)
  static bool is_using_archive()                             NOT_CDS_RETURN_(false);
  static bool is_using_only_default_archive()                NOT_CDS_RETURN_(false);

  // static_archive
  static bool is_dumping_static_archive()                    { return CDS_ONLY(_is_dumping_static_archive) NOT_CDS(false); }
  static void enable_dumping_static_archive()                { CDS_ONLY(_is_dumping_static_archive = true); }

  // A static CDS archive can be dumped in three modes:
  //
  // "classic"   - This is the traditional CDS workflow of
  //               "java -Xshare:dump -XX:SharedClassListFile=file.txt".
  //
  // "preimage"  - This happens when we execute the JEP 483 training run, e.g:
  //               "java -XX:AOTMode=record -XX:AOTConfiguration=app.aotconfig -cp app.jar App"
  //               The above command writes app.aotconfig as a "CDS preimage". This
  //               is a binary file that contains all the classes loaded during the
  //               training run, plus profiling data (e.g., the resolved constant pool entries).
  //
  // "final"     - This happens when we execute the JEP 483 assembly phase, e.g:
  //               "java -XX:AOTMode=create -XX:AOTConfiguration=app.aotconfig -XX:AOTCache=app.aot -cp app.jar"
  //               The above command loads all classes from app.aotconfig, perform additional linking,
  //               and writes app.aot as a "CDS final image" file.
  //
  // The main structural difference between "preimage" and "final" is that the preimage
  // - has a different magic number (0xcafea07c)
  // - does not have any archived Java heap objects
  // - does not have aot-linked classes
  static bool is_dumping_classic_static_archive()            NOT_CDS_RETURN_(false);
  static bool is_dumping_preimage_static_archive()           NOT_CDS_RETURN_(false);
  static bool is_dumping_final_static_archive()              NOT_CDS_RETURN_(false);

  // dynamic_archive
  static bool is_dumping_dynamic_archive()                   { return CDS_ONLY(_is_dumping_dynamic_archive) NOT_CDS(false); }
  static void enable_dumping_dynamic_archive(const char* output_path) NOT_CDS_RETURN;
  static void disable_dumping_dynamic_archive()              { CDS_ONLY(_is_dumping_dynamic_archive = false); }

  // Misc CDS features
  static bool allow_only_single_java_thread()                NOT_CDS_RETURN_(false);

  static bool is_single_command_training()                   { return CDS_ONLY(_is_single_command_training) NOT_CDS(false); }
  static bool has_temp_aot_config_file()                     { return CDS_ONLY(_has_temp_aot_config_file) NOT_CDS(false); }

  // This is *Legacy* optimization for lambdas before JEP 483. May be removed in the future.
  static bool is_dumping_lambdas_in_legacy_mode()            NOT_CDS_RETURN_(false);

  // optimized_module_handling -- can we skip some expensive operations related to modules?
  static bool is_using_optimized_module_handling()           { return CDS_ONLY(_is_using_optimized_module_handling) NOT_CDS(false); }
  static void stop_using_optimized_module_handling()         NOT_CDS_RETURN;

  static bool is_logging_lambda_form_invokers()              NOT_CDS_RETURN_(false);
  static bool is_dumping_regenerated_lambdaform_invokers()   NOT_CDS_RETURN_(false);

  static bool is_dumping_aot_linked_classes()                NOT_CDS_JAVA_HEAP_RETURN_(false);
  static bool is_using_aot_linked_classes()                  NOT_CDS_JAVA_HEAP_RETURN_(false);
  static void set_has_aot_linked_classes(bool has_aot_linked_classes) NOT_CDS_JAVA_HEAP_RETURN;

  // Bytecode verification
  static bool is_preserving_verification_constraints();
  static bool is_old_class_for_verifier(const InstanceKlass* ik);

  // archive_path

  // Points to the classes.jsa in $JAVA_HOME (could be input or output)
  static const char* default_archive_path()                  NOT_CDS_RETURN_(nullptr);
  static const char* input_static_archive_path()             { return CDS_ONLY(_input_static_archive_path) NOT_CDS(nullptr); }
  static const char* input_dynamic_archive_path()            { return CDS_ONLY(_input_dynamic_archive_path) NOT_CDS(nullptr); }
  static const char* output_archive_path()                   { return CDS_ONLY(_output_archive_path) NOT_CDS(nullptr); }

  // --- Archived java objects

  static bool are_vm_options_incompatible_with_dumping_heap() NOT_CDS_JAVA_HEAP_RETURN_(true);
  static void log_reasons_for_not_dumping_heap();

  static void disable_heap_dumping()                         { CDS_ONLY(_disable_heap_dumping = true); }
  static bool is_dumping_heap()                              NOT_CDS_JAVA_HEAP_RETURN_(false);
  static bool is_loading_heap()                              NOT_CDS_JAVA_HEAP_RETURN_(false);

  static bool is_dumping_klass_subgraphs()                   NOT_CDS_JAVA_HEAP_RETURN_(false);
  static bool is_using_klass_subgraphs()                     NOT_CDS_JAVA_HEAP_RETURN_(false);

  static bool is_dumping_invokedynamic()                     NOT_CDS_JAVA_HEAP_RETURN_(false);
  static bool is_dumping_method_handles()                    NOT_CDS_JAVA_HEAP_RETURN_(false);

  // full_module_graph (requires optimized_module_handling)
  static bool is_dumping_full_module_graph()                 { return CDS_ONLY(_is_dumping_full_module_graph) NOT_CDS(false); }
  static bool is_using_full_module_graph()                   NOT_CDS_JAVA_HEAP_RETURN_(false);
  static void stop_dumping_full_module_graph(const char* reason = nullptr) NOT_CDS_JAVA_HEAP_RETURN;
  static void stop_using_full_module_graph(const char* reason = nullptr) NOT_CDS_JAVA_HEAP_RETURN;

  // --- AOT code

  static bool is_dumping_aot_code()                          NOT_CDS_RETURN_(false);
  static void disable_dumping_aot_code()                     NOT_CDS_RETURN;
  static void enable_dumping_aot_code()                      NOT_CDS_RETURN;
  static bool is_dumping_adapters()                          NOT_CDS_RETURN_(false);

  // Some CDS functions assume that they are called only within a single-threaded context. I.e.,
  // they are called from:
  //    - The VM thread (e.g., inside VM_PopulateDumpSharedSpace)
  //    - The thread that performs prepatory steps before switching to the VM thread
  // Since these two threads never execute concurrently, we can avoid using locks in these CDS
  // function. For safety, these functions should assert with CDSConfig::current_thread_is_vm_or_dumper().
  class DumperThreadMark {
  public:
    DumperThreadMark(JavaThread* current);
    ~DumperThreadMark();
  };

  static bool current_thread_is_dumper() NOT_CDS_RETURN_(false);
  static bool current_thread_is_vm_or_dumper() NOT_CDS_RETURN_(false);
};

#endif // SHARE_CDS_CDSCONFIG_HPP
