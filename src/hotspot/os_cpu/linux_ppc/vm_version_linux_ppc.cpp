/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026 SAP SE. All rights reserved.
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

#include "runtime/vm_version.hpp"

#include <unistd.h>

int VM_Version::get_dcache_line_size() {
  // This should work on all modern linux versions:
  int size = sysconf(_SC_LEVEL1_DCACHE_LINESIZE);
  // It may fail with very old linux / glibc versions. We use DEFAULT_CACHE_LINE_SIZE in this case.
  // That is the correct value for all currently supported processors.
  return (size <= 0) ? DEFAULT_CACHE_LINE_SIZE : size;
}

int VM_Version::get_icache_line_size() {
  // This should work on all modern linux versions:
  int size = sysconf(_SC_LEVEL1_ICACHE_LINESIZE);
  // It may fail with very old linux / glibc versions. We use DEFAULT_CACHE_LINE_SIZE in this case.
  // That is the correct value for all currently supported processors.
  return (size <= 0) ? DEFAULT_CACHE_LINE_SIZE : size;
}
