/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SRC_SHARE_VM_GC_SHARED_MEMSETWITHCONCURRENTREADERS_HPP
#define SRC_SHARE_VM_GC_SHARED_MEMSETWITHCONCURRENTREADERS_HPP

#include <stddef.h>
#include <string.h>
#include "utilities/macros.hpp"

// Only used by concurrent collectors.
#if INCLUDE_ALL_GCS

// Fill a block of memory with value, like memset, but with the
// understanding that there may be concurrent readers of that memory.
void memset_with_concurrent_readers(void* to, int value, size_t size);

#ifdef TARGET_ARCH_sparc

// SPARC requires special handling.  See SPARC-specific definition.

#else
// All others just use memset.

inline void memset_with_concurrent_readers(void* to, int value, size_t size) {
  ::memset(to, value, size);
}

#endif // End of target dispatch.

#endif // INCLUDE_ALL_GCS

#endif // include guard
