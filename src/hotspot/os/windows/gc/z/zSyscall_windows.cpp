/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "gc/shared/gcLogPrecious.hpp"
#include "gc/z/zSyscall_windows.hpp"
#include "runtime/java.hpp"
#include "runtime/os.hpp"

ZSyscall::CreateFileMappingWFn ZSyscall::CreateFileMappingW;
ZSyscall::VirtualAlloc2Fn ZSyscall::VirtualAlloc2;
ZSyscall::VirtualFreeExFn ZSyscall::VirtualFreeEx;
ZSyscall::MapViewOfFile3Fn ZSyscall::MapViewOfFile3;
ZSyscall::UnmapViewOfFile2Fn ZSyscall::UnmapViewOfFile2;

template <typename Fn>
static void lookup_symbol(Fn*& fn, const char* library, const char* symbol) {
  char ebuf[1024];
  void* const handle = os::dll_load(library, ebuf, sizeof(ebuf));
  if (handle == NULL) {
    log_error_p(gc)("Failed to load library: %s", library);
    vm_exit_during_initialization("ZGC requires Windows version 1803 or later");
  }

  fn = reinterpret_cast<Fn*>(os::dll_lookup(handle, symbol));
  if (fn == NULL) {
    log_error_p(gc)("Failed to lookup symbol: %s", symbol);
    vm_exit_during_initialization("ZGC requires Windows version 1803 or later");
  }
}

void ZSyscall::initialize() {
  lookup_symbol(CreateFileMappingW, "KernelBase", "CreateFileMappingW");
  lookup_symbol(VirtualAlloc2,      "KernelBase", "VirtualAlloc2");
  lookup_symbol(VirtualFreeEx,      "KernelBase", "VirtualFreeEx");
  lookup_symbol(MapViewOfFile3,     "KernelBase", "MapViewOfFile3");
  lookup_symbol(UnmapViewOfFile2,   "KernelBase", "UnmapViewOfFile2");
}

bool ZSyscall::is_supported() {
  char ebuf[1024];
  void* const handle = os::dll_load("KernelBase", ebuf, sizeof(ebuf));
  if (handle == NULL) {
    assert(false, "Failed to load library: KernelBase");
    return false;
  }

  return os::dll_lookup(handle, "VirtualAlloc2") != NULL;
}
