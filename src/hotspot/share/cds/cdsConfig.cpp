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
#include "logging/log.hpp"

bool CDSConfig::_is_dumping_dynamic_archive = false;
bool CDSConfig::_enable_dumping_full_module_graph = true;
bool CDSConfig::_enable_loading_full_module_graph = true;

bool CDSConfig::is_dumping_static_archive() {
  return DumpSharedSpaces;
}

#if INCLUDE_CDS_JAVA_HEAP
bool CDSConfig::is_dumping_heap() {
  // heap dump is not supported in dynamic dump
  return is_dumping_static_archive() && HeapShared::can_write();
}

bool CDSConfig::is_dumping_full_module_graph() {
  if (is_dumping_heap() &&
      MetaspaceShared::use_optimized_module_handling() &&
      _enable_dumping_full_module_graph) {
    return true;
  } else {
    return false;
  }
}

bool CDSConfig::is_loading_full_module_graph() {
  if (ClassLoaderDataShared::is_full_module_graph_loaded()) {
    return true;
  }

  if (UseSharedSpaces &&
      ArchiveHeapLoader::can_use() &&
      MetaspaceShared::use_optimized_module_handling() &&
      _enable_loading_full_module_graph) {
    // Classes used by the archived full module graph are loaded in JVMTI early phase.
    assert(!(JvmtiExport::should_post_class_file_load_hook() && JvmtiExport::has_early_class_hook_env()),
           "CDS should be disabled if early class hooks are enabled");
    return true;
  } else {
    return false;
  }
}

void CDSConfig::disable_dumping_full_module_graph(const char* reason) {
  if (_enable_dumping_full_module_graph) {
    _enable_dumping_full_module_graph = false;
    if (reason != nullptr) {
      log_info(cds)("full module graph cannot be dumped: %s", reason);
    }
  }
}

void CDSConfig::disable_loading_full_module_graph(const char* reason) {
  if (_enable_loading_full_module_graph) {
    _enable_loading_full_module_graph = false;
    if (reason != nullptr) {
      log_info(cds)("full module graph cannot be loaded: %s", reason);
    }
  }
}
#endif // INCLUDE_CDS_JAVA_HEAP
