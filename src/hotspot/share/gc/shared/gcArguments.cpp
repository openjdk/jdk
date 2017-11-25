/*
 * Copyright (c) 2017, Red Hat, Inc. and/or its affiliates.
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
#include "gc/shared/gcArguments.hpp"
#include "gc/serial/serialArguments.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/arguments.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/macros.hpp"

#if INCLUDE_ALL_GCS
#include "gc/parallel/parallelArguments.hpp"
#include "gc/cms/cmsArguments.hpp"
#include "gc/g1/g1Arguments.hpp"
#endif

GCArguments* GCArguments::_instance = NULL;

GCArguments* GCArguments::arguments() {
  assert(is_initialized(), "Heap factory not yet created");
  return _instance;
}

bool GCArguments::is_initialized() {
  return _instance != NULL;
}

bool GCArguments::gc_selected() {
#if INCLUDE_ALL_GCS
  return UseSerialGC || UseParallelGC || UseParallelOldGC || UseConcMarkSweepGC || UseG1GC;
#else
  return UseSerialGC;
#endif // INCLUDE_ALL_GCS
}

void GCArguments::select_gc() {
  if (!gc_selected()) {
    select_gc_ergonomically();
    if (!gc_selected()) {
      vm_exit_during_initialization("Garbage collector not selected (default collector explicitly disabled)", NULL);
    }
  }
}

void GCArguments::select_gc_ergonomically() {
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

void GCArguments::initialize_flags() {
#if INCLUDE_ALL_GCS
  if (MinHeapFreeRatio == 100) {
    // Keeping the heap 100% free is hard ;-) so limit it to 99%.
    FLAG_SET_ERGO(uintx, MinHeapFreeRatio, 99);
  }

  // If class unloading is disabled, also disable concurrent class unloading.
  if (!ClassUnloading) {
    FLAG_SET_CMDLINE(bool, CMSClassUnloadingEnabled, false);
    FLAG_SET_CMDLINE(bool, ClassUnloadingWithConcurrentMark, false);
  }
#endif // INCLUDE_ALL_GCS
}

jint GCArguments::initialize() {
  assert(!is_initialized(), "GC arguments already initialized");

  select_gc();

#if !INCLUDE_ALL_GCS
  if (UseParallelGC || UseParallelOldGC) {
    jio_fprintf(defaultStream::error_stream(), "UseParallelGC not supported in this VM.\n");
    return JNI_ERR;
  } else if (UseG1GC) {
    jio_fprintf(defaultStream::error_stream(), "UseG1GC not supported in this VM.\n");
    return JNI_ERR;
  } else if (UseConcMarkSweepGC) {
    jio_fprintf(defaultStream::error_stream(), "UseConcMarkSweepGC not supported in this VM.\n");
    return JNI_ERR;
#else
  if (UseParallelGC || UseParallelOldGC) {
    _instance = new ParallelArguments();
  } else if (UseG1GC) {
    _instance = new G1Arguments();
  } else if (UseConcMarkSweepGC) {
    _instance = new CMSArguments();
#endif
  } else if (UseSerialGC) {
    _instance = new SerialArguments();
  } else {
    ShouldNotReachHere();
  }
  return JNI_OK;
}
