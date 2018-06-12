/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zRelocationSet.hpp"
#include "memory/allocation.inline.hpp"

ZRelocationSet::ZRelocationSet() :
    _pages(NULL),
    _npages(0) {}

void ZRelocationSet::populate(const ZPage* const* group0, size_t ngroup0,
                              const ZPage* const* group1, size_t ngroup1) {
  _npages = ngroup0 + ngroup1;
  _pages = REALLOC_C_HEAP_ARRAY(ZPage*, _pages, _npages, mtGC);

  if (_pages != NULL) {
    if (group0 != NULL) {
      memcpy(_pages, group0, ngroup0 * sizeof(ZPage*));
    }
    if (group1 != NULL) {
      memcpy(_pages + ngroup0, group1, ngroup1 * sizeof(ZPage*));
    }
  }
}
