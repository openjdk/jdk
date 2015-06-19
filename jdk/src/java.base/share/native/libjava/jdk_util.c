/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <assert.h>

#include "jvm.h"
#include "jdk_util.h"

JNIEXPORT void
JDK_GetVersionInfo0(jdk_version_info* info, size_t info_size) {
    /* These VERSION_* macros are given by the build system */
    const unsigned int version_major = VERSION_MAJOR;
    const unsigned int version_minor = VERSION_MINOR;
    const unsigned int version_security = VERSION_SECURITY;
    const unsigned int version_patch = VERSION_PATCH;
    const unsigned int version_build = VERSION_BUILD;

    memset(info, 0, info_size);
    info->jdk_version = ((version_major & 0xFF) << 24) |
                        ((version_minor & 0xFF) << 16) |
                        ((version_security & 0xFF) << 8)  |
                        (version_build & 0xFF);
    info->patch_version = version_patch;
    info->thread_park_blocker = 1;
    // Advertise presence of sun.misc.PostVMInitHook:
    // future optimization: detect if this is enabled.
    info->post_vm_init_hook_enabled = 1;
    info->pending_list_uses_discovered_field = 1;
}
