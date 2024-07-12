/*
 * Copyright (c) 2020, 2024, Red Hat, Inc.
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
#include <unistd.h>
#include <sys/sysinfo.h>

#include "jni.h"
#include "jvm.h"

#include "jdk_internal_platform_CgroupMetrics.h"

JNIEXPORT jboolean JNICALL
Java_jdk_internal_platform_CgroupMetrics_isUseContainerSupport(JNIEnv *env, jclass ignored)
{
    return JVM_IsUseContainerSupport();
}

JNIEXPORT jboolean JNICALL
Java_jdk_internal_platform_CgroupMetrics_isContainerized0(JNIEnv *env, jclass ignored)
{
    return JVM_IsContainerized();
}

JNIEXPORT jlong JNICALL
Java_jdk_internal_platform_CgroupMetrics_getTotalMemorySize0
  (JNIEnv *env, jclass ignored)
{
    jlong pages = sysconf(_SC_PHYS_PAGES);
    jlong page_size = sysconf(_SC_PAGESIZE);
    return pages * page_size;
}

JNIEXPORT jlong JNICALL
Java_jdk_internal_platform_CgroupMetrics_getTotalSwapSize0
  (JNIEnv *env, jclass ignored)
{
    struct sysinfo si;
    int retval = sysinfo(&si);
    if (retval < 0) {
         return 0; // syinfo failed, treat as no swap
    }
    return (jlong)(si.totalswap * si.mem_unit);
}
