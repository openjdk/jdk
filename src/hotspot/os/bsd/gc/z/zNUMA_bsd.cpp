/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/gc_globals.hpp"
#include "gc/z/zCPU.inline.hpp"
#include "gc/z/zNUMA.inline.hpp"
#include "runtime/globals_extension.hpp"

void ZNUMA::pd_initialize() {
  _enabled = false;
  _count = !FLAG_IS_DEFAULT(ZFakeNUMA)
      ? ZFakeNUMA
      : 1;
}

uint32_t ZNUMA::id() {
  if (is_faked()) {
    // ZFakeNUMA testing, ignores _enabled
    return ZCPU::id() % ZFakeNUMA;
  }

  return 0;
}

uint32_t ZNUMA::memory_id(uintptr_t addr) {
  // NUMA support not enabled, assume everything belongs to node zero
  return 0;
}
