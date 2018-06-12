/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/collectedHeap.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "utilities/debug.hpp"

#include <stdlib.h>

uintptr_t ZUtils::alloc_aligned(size_t alignment, size_t size) {
  void* res = NULL;

  if (posix_memalign(&res, alignment, size) != 0) {
    fatal("posix_memalign() failed");
  }

  memset(res, 0, size);

  return (uintptr_t)res;
}

void ZUtils::insert_filler_object(uintptr_t addr, size_t size) {
  const size_t fill_size_in_words = bytes_to_words(size);
  if (fill_size_in_words >= CollectedHeap::min_fill_size()) {
    CollectedHeap::fill_with_objects((HeapWord*)ZAddress::good(addr), fill_size_in_words);
  }
}
