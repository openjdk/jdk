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

#ifndef SHARE_CDS_CDSCONFIG_HPP
#define SHARE_CDS_CDSCONFIG_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

class CDSConfig : public AllStatic {
#if INCLUDE_CDS
  static bool _is_dumping_static_archive;
  static bool _is_dumping_dynamic_archive;
  static bool _dumping_full_module_graph_disabled;
  static bool _loading_full_module_graph_disabled;

  static char*  _default_archive_path;
  static char*  _static_archive_path;
  static char*  _dynamic_archive_path;
#endif

  static void extract_shared_archive_paths(const char* archive_path,
                                           char** base_archive_path,
                                           char** top_archive_path);
  static void init_shared_archive_paths();
  static bool check_unsupported_cds_runtime_properties();

public:
  // Initialization and command-line checking
  static void initialize() NOT_CDS_RETURN;
  static void check_system_property(const char* key, const char* value) NOT_CDS_RETURN;
  static void check_unsupported_dumping_properties() NOT_CDS_RETURN;
  static bool check_vm_args_consistency(bool patch_mod_javabase,  bool mode_flag_cmd_line) NOT_CDS_RETURN_(true);

  // Basic CDS features
  static bool      is_dumping_archive()                      { return is_dumping_static_archive() || is_dumping_dynamic_archive(); }
  static bool      is_dumping_static_archive()               { return CDS_ONLY(_is_dumping_static_archive) NOT_CDS(false); }
  static void  enable_dumping_static_archive()               { CDS_ONLY(_is_dumping_static_archive = true); }
  static bool      is_dumping_dynamic_archive()              { return CDS_ONLY(_is_dumping_dynamic_archive) NOT_CDS(false); }
  static void  enable_dumping_dynamic_archive()              { CDS_ONLY(_is_dumping_dynamic_archive = true); }
  static void disable_dumping_dynamic_archive()              { CDS_ONLY(_is_dumping_dynamic_archive = false); }

  // Archive paths
  // Points to the classes.jsa in $JAVA_HOME
  static char* default_archive_path()                         NOT_CDS_RETURN_(nullptr);
  // The actual static archive  (if any) selected at runtime
  static const char* static_archive_path()                   { return CDS_ONLY(_static_archive_path) NOT_CDS(nullptr); }
  // The actual dynamic archive  (if any) selected at runtime
  static const char* dynamic_archive_path()                  { return CDS_ONLY(_dynamic_archive_path) NOT_CDS(nullptr); }

  static int num_archives(const char* archive_path)          NOT_CDS_RETURN_(0);


  // CDS archived heap
  static bool      is_dumping_heap()                         NOT_CDS_JAVA_HEAP_RETURN_(false);
  static void disable_dumping_full_module_graph(const char* reason = nullptr) NOT_CDS_JAVA_HEAP_RETURN;
  static bool      is_dumping_full_module_graph()            NOT_CDS_JAVA_HEAP_RETURN_(false);
  static void disable_loading_full_module_graph(const char* reason = nullptr) NOT_CDS_JAVA_HEAP_RETURN;
  static bool      is_loading_full_module_graph()            NOT_CDS_JAVA_HEAP_RETURN_(false);

};

#endif // SHARE_CDS_CDSCONFIG_HPP
