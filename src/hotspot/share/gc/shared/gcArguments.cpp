/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/arguments.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/macros.hpp"

void GCArguments::initialize() {
  if (FullGCALot && FLAG_IS_DEFAULT(MarkSweepAlwaysCompactCount)) {
    MarkSweepAlwaysCompactCount = 1;  // Move objects every gc.
  }

  if (!(UseParallelGC || UseParallelOldGC) && FLAG_IS_DEFAULT(ScavengeBeforeFullGC)) {
    FLAG_SET_DEFAULT(ScavengeBeforeFullGC, false);
  }

  if (GCTimeLimit == 100) {
    // Turn off gc-overhead-limit-exceeded checks
    FLAG_SET_DEFAULT(UseGCOverheadLimit, false);
  }

  if (MinHeapFreeRatio == 100) {
    // Keeping the heap 100% free is hard ;-) so limit it to 99%.
    FLAG_SET_ERGO(uintx, MinHeapFreeRatio, 99);
  }

  if (!ClassUnloading) {
    // If class unloading is disabled, also disable concurrent class unloading.
    FLAG_SET_CMDLINE(bool, ClassUnloadingWithConcurrentMark, false);
  }

  if (!FLAG_IS_DEFAULT(AllocateOldGenAt)) {
    // CompressedOops not supported when AllocateOldGenAt is set.
    LP64_ONLY(FLAG_SET_DEFAULT(UseCompressedOops, false));
    LP64_ONLY(FLAG_SET_DEFAULT(UseCompressedClassPointers, false));
    // When AllocateOldGenAt is set, we cannot use largepages for entire heap memory.
    // Only young gen which is allocated in dram can use large pages, but we currently don't support that.
    FLAG_SET_DEFAULT(UseLargePages, false);
  }
}

bool GCArguments::check_args_consistency() {
  bool status = true;
  if (!FLAG_IS_DEFAULT(AllocateHeapAt) && !FLAG_IS_DEFAULT(AllocateOldGenAt)) {
    jio_fprintf(defaultStream::error_stream(),
      "AllocateHeapAt and AllocateOldGenAt cannot be used together.\n");
    status = false;
  }
  if (!FLAG_IS_DEFAULT(AllocateOldGenAt) && (UseSerialGC || UseConcMarkSweepGC || UseEpsilonGC || UseZGC)) {
    jio_fprintf(defaultStream::error_stream(),
      "AllocateOldGenAt is not supported for selected GC.\n");
    status = false;
  }
  return status;
}
