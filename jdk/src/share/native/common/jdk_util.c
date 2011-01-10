/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef JDK_UPDATE_VERSION
   /* if not defined set to 00 */
   #define JDK_UPDATE_VERSION "00"
#endif

JNIEXPORT void
JDK_GetVersionInfo0(jdk_version_info* info, size_t info_size) {
    /* These JDK_* macros are set at Makefile or the command line */
    const unsigned int jdk_major_version =
        (unsigned int) atoi(JDK_MAJOR_VERSION);
    const unsigned int jdk_minor_version =
        (unsigned int) atoi(JDK_MINOR_VERSION);
    const unsigned int jdk_micro_version =
        (unsigned int) atoi(JDK_MICRO_VERSION);

    const char* jdk_build_string = JDK_BUILD_NUMBER;
    char build_number[4];
    unsigned int jdk_build_number = 0;

    const char* jdk_update_string = JDK_UPDATE_VERSION;
    unsigned int jdk_update_version = 0;
    char update_ver[3];
    char jdk_special_version = '\0';

    /* If the JDK_BUILD_NUMBER is of format bXX and XX is an integer
     * XX is the jdk_build_number.
     */
    int len = strlen(jdk_build_string);
    if (jdk_build_string[0] == 'b' && len >= 2) {
        int i = 0;
        for (i = 1; i < len; i++) {
            if (isdigit(jdk_build_string[i])) {
                build_number[i-1] = jdk_build_string[i];
            } else {
                // invalid build number
                i = -1;
                break;
            }
        }
        if (i == len) {
            build_number[len-1] = '\0';
            jdk_build_number = (unsigned int) atoi(build_number) ;
        }
    }

    assert(jdk_build_number >= 0 && jdk_build_number <= 255);

    if (strlen(jdk_update_string) == 2 || strlen(jdk_update_string) == 3) {
        if (isdigit(jdk_update_string[0]) && isdigit(jdk_update_string[1])) {
            update_ver[0] = jdk_update_string[0];
            update_ver[1] = jdk_update_string[1];
            update_ver[2] = '\0';
            jdk_update_version = (unsigned int) atoi(update_ver);
            if (strlen(jdk_update_string) == 3) {
                jdk_special_version = jdk_update_string[2];
            }
        }
    }

    memset(info, 0, info_size);
    info->jdk_version = ((jdk_major_version & 0xFF) << 24) |
                        ((jdk_minor_version & 0xFF) << 16) |
                        ((jdk_micro_version & 0xFF) << 8)  |
                        (jdk_build_number & 0xFF);
    info->update_version = jdk_update_version;
    info->special_update_version = (unsigned int) jdk_special_version;
    info->thread_park_blocker = 1;
}
