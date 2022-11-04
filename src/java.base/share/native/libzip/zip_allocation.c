/*
 * Copyright (c) 2022 SAP. All rights reserved.
 * Copyright (c) 1995, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/*
 * Support for reading ZIP/JAR files.
 */

#include <string.h>
#include "zip_allocation.h"

/* Function prototypes must exactly match zalloc and zfree. */
static voidpf local_allocation(voidpf opaque, uInt items, uInt size) {
    return JVM_MemoryCalloc(items, size, (allocation_category_t)opaque);
}

static void local_deallocation(voidpf opaque, voidpf address) {
    JVM_MemoryFree(address);
}

JNIEXPORT void ZIP_InitializeStreamAllocationHooks(z_stream* strm, allocation_category_t cat) {
    strm->zalloc = local_allocation;
    strm->zfree = local_deallocation;
    strm->opaque = (voidpf) cat;
}

