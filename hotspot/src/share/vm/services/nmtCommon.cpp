/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "services/nmtCommon.hpp"

const char* NMTUtil::_memory_type_names[] = {
  "Java Heap",
  "Class",
  "Thread",
  "Thread Stack",
  "Code",
  "GC",
  "Compiler",
  "Internal",
  "Other",
  "Symbol",
  "Native Memory Tracking",
  "Shared class space",
  "Arena Chunk",
  "Test",
  "Tracing",
  "Logging",
  "Arguments",
  "Unknown"
};


const char* NMTUtil::scale_name(size_t scale) {
  switch(scale) {
    case K: return "KB";
    case M: return "MB";
    case G: return "GB";
  }
  ShouldNotReachHere();
  return NULL;
}

size_t NMTUtil::scale_from_name(const char* scale) {
  assert(scale != NULL, "Null pointer check");
  if (strncmp(scale, "KB", 2) == 0 ||
      strncmp(scale, "kb", 2) == 0) {
    return K;
  } else if (strncmp(scale, "MB", 2) == 0 ||
             strncmp(scale, "mb", 2) == 0) {
    return M;
  } else if (strncmp(scale, "GB", 2) == 0 ||
             strncmp(scale, "gb", 2) == 0) {
    return G;
  } else {
    return 0; // Invalid value
  }
  return K;
}

