/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/serial/serialArguments.hpp"
#include "gc/shared/gcConfig.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc/parallel/parallelArguments.hpp"
#include "gc/cms/cmsArguments.hpp"
#include "gc/g1/g1Arguments.hpp"
#endif // INCLUDE_ALL_GCS

struct SupportedGC {
  bool&               _flag;
  CollectedHeap::Name _name;
  GCArguments&        _arguments;

  SupportedGC(bool& flag, CollectedHeap::Name name, GCArguments& arguments) :
      _flag(flag), _name(name), _arguments(arguments) {}
};

static SerialArguments   serialArguments;
#if INCLUDE_ALL_GCS
static ParallelArguments parallelArguments;
static CMSArguments      cmsArguments;
static G1Arguments       g1Arguments;
#endif // INCLUDE_ALL_GCS

// Table of supported GCs, for translating between command
// line flag, CollectedHeap::Name and GCArguments instance.
static const SupportedGC SupportedGCs[] = {
  SupportedGC(UseSerialGC,        CollectedHeap::Serial,   serialArguments),
#if INCLUDE_ALL_GCS
  SupportedGC(UseParallelGC,      CollectedHeap::Parallel, parallelArguments),
  SupportedGC(UseParallelOldGC,   CollectedHeap::Parallel, parallelArguments),
  SupportedGC(UseConcMarkSweepGC, CollectedHeap::CMS,      cmsArguments),
  SupportedGC(UseG1GC,            CollectedHeap::G1,       g1Arguments),
#endif // INCLUDE_ALL_GCS
};

GCArguments* GCConfig::_arguments = NULL;
bool GCConfig::_gc_selected_ergonomically = false;

void GCConfig::select_gc_ergonomically() {
#if INCLUDE_ALL_GCS
  if (os::is_server_class_machine()) {
    FLAG_SET_ERGO_IF_DEFAULT(bool, UseG1GC, true);
  } else {
    FLAG_SET_ERGO_IF_DEFAULT(bool, UseSerialGC, true);
  }
#else
  UNSUPPORTED_OPTION(UseG1GC);
  UNSUPPORTED_OPTION(UseParallelGC);
  UNSUPPORTED_OPTION(UseParallelOldGC);
  UNSUPPORTED_OPTION(UseConcMarkSweepGC);
  FLAG_SET_ERGO_IF_DEFAULT(bool, UseSerialGC, true);
#endif // INCLUDE_ALL_GCS
}

bool GCConfig::is_no_gc_selected() {
  for (size_t i = 0; i < ARRAY_SIZE(SupportedGCs); i++) {
    if (SupportedGCs[i]._flag) {
      return false;
    }
  }

  return true;
}

bool GCConfig::is_exactly_one_gc_selected() {
  CollectedHeap::Name selected = CollectedHeap::None;

  for (size_t i = 0; i < ARRAY_SIZE(SupportedGCs); i++) {
    if (SupportedGCs[i]._flag) {
      if (SupportedGCs[i]._name == selected || selected == CollectedHeap::None) {
        // Selected
        selected = SupportedGCs[i]._name;
      } else {
        // More than one selected
        return false;
      }
    }
  }

  return selected != CollectedHeap::None;
}

GCArguments* GCConfig::select_gc() {
  if (is_no_gc_selected()) {
    // Try select GC ergonomically
    select_gc_ergonomically();

    if (is_no_gc_selected()) {
      // Failed to select GC ergonomically
      vm_exit_during_initialization("Garbage collector not selected "
                                    "(default collector explicitly disabled)", NULL);
    }

    // Succeeded to select GC ergonomically
    _gc_selected_ergonomically = true;
  }

  if (is_exactly_one_gc_selected()) {
    // Exacly one GC selected
    for (size_t i = 0; i < ARRAY_SIZE(SupportedGCs); i++) {
      if (SupportedGCs[i]._flag) {
        return &SupportedGCs[i]._arguments;
      }
    }
  }

  // More than one GC selected
  vm_exit_during_initialization("Multiple garbage collectors selected", NULL);

  return NULL;
}

void GCConfig::initialize() {
  assert(_arguments == NULL, "Already initialized");
  _arguments = select_gc();
}

bool GCConfig::is_gc_supported(CollectedHeap::Name name) {
  for (size_t i = 0; i < ARRAY_SIZE(SupportedGCs); i++) {
    if (SupportedGCs[i]._name == name) {
      // Supported
      return true;
    }
  }

  // Not supported
  return false;
}

bool GCConfig::is_gc_selected(CollectedHeap::Name name) {
  for (size_t i = 0; i < ARRAY_SIZE(SupportedGCs); i++) {
    if (SupportedGCs[i]._name == name && SupportedGCs[i]._flag) {
      // Selected
      return true;
    }
  }

  // Not selected
  return false;
}

bool GCConfig::is_gc_selected_ergonomically() {
  return _gc_selected_ergonomically;
}

GCArguments* GCConfig::arguments() {
  assert(_arguments != NULL, "Not initialized");
  return _arguments;
}
