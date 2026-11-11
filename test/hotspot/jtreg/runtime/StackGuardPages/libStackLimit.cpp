/*
 * Copyright (c) 2026, Microsoft and/or its affiliates. All rights reserved.
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

#include <windows.h>

extern "C" __declspec(dllexport) int __cdecl checkStackLimit() {
  PNT_TIB old_tib = (PNT_TIB) NtCurrentTeb();
  ULONG_PTR before = (ULONG_PTR) old_tib->StackLimit;

  // Compute an address that's comfortably below the existing `StackLimit`.
  const size_t offset = 256 * 1024;
  volatile unsigned char* faultAddr =
      reinterpret_cast<volatile unsigned char*>(before - offset);

  // Trigger HotSpot's access violation handler, which will commit pages.
  *faultAddr = 0;

  // Correct recovery must lower StackLimit to or below the touched address.
  PNT_TIB new_tib = (PNT_TIB) NtCurrentTeb();
  ULONG_PTR after = (ULONG_PTR) new_tib->StackLimit;
  ULONG_PTR faulted = reinterpret_cast<ULONG_PTR>(faultAddr);
  return after <= faulted ? 0 : 1;
}
