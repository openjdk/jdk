/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2015, 2025 SAP SE. All rights reserved.
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

/* Empty stubs for now to satisfy the new build process.                 */
/* Implement and update https://bugs.openjdk.org/browse/JDK-8030957 */

#include <jni.h>
#include <time.h>
#include <stdlib.h>
#include <libperfstat.h>
#include "com_sun_management_internal_OperatingSystemImpl.h"
perfstat_process_t prev_stats = {0};
static unsigned long long prev_timebase = 0;
static int initialized = 0;

#define HTIC2SEC(x) (((double)(x) * XINTFRAC) / 1000000000.0)

static perfstat_cpu_total_t cpu_total_old;
static time_t last_sample_time = 0;
static double last_cpu_load = -1.0;
JNIEXPORT jdouble JNICALL
Java_com_sun_management_internal_OperatingSystemImpl_getCpuLoad0
(JNIEnv *env, jobject dummy)
{
    perfstat_cpu_total_t cpu_total;
    int ret;

    time_t now = time(NULL);
    if (initialized && (now - last_sample_time < 5)) {
        return last_cpu_load; // Return cached value if less than 5s
    }

    ret = perfstat_cpu_total(NULL, &cpu_total, sizeof(perfstat_cpu_total_t), 1);
    if (ret < 0) {
        return -1.0;
    }

    if (!initialized) {
        cpu_total_old = cpu_total;
        initialized = 1;
        last_sample_time = now;
        return -1.0; // Not enough data yet
    }

    long long user_diff = cpu_total.user - cpu_total_old.user;
    long long sys_diff = cpu_total.sys - cpu_total_old.sys;
    long long idle_diff = cpu_total.idle - cpu_total_old.idle;
    long long wait_diff = cpu_total.wait - cpu_total_old.wait;
    long long total = user_diff + sys_diff + idle_diff + wait_diff;

    if (total == 0) {
        return -1.0;
    }

    printf("User diff: %lld\n", user_diff);
    printf("Sys diff: %lld\n", sys_diff);
    printf("Idle diff: %lld\n", idle_diff);
    printf("Wait diff: %lld\n", wait_diff);


    double load = (double)(user_diff + sys_diff) / total;
    printf("load:%.4f\n",load);    
    fflush(stdout);
    last_cpu_load = load;
    last_sample_time = now;
    cpu_total_old = cpu_total;

    return load;
}

JNIEXPORT jdouble JNICALL
Java_com_sun_management_internal_OperatingSystemImpl_getProcessCpuLoad0
(JNIEnv *env, jobject dummy)
{
    perfstat_process_t curr_stats;
    perfstat_id_t id;
    unsigned long long curr_timebase, timebase_diff;
    double user_diff, sys_diff, delta_time;

    if (perfstat_process(&id, &curr_stats, sizeof(perfstat_process_t), 1) < 0) {
        return -1.0;  
    }
    if (!initialized) {
        prev_stats = curr_stats;
        prev_timebase = curr_stats.last_timebase;
        initialized = 1;
        return -1.0;
    }
    printf("initialised done");
    curr_timebase = curr_stats.last_timebase;
    timebase_diff = curr_timebase - prev_timebase;
    
    if ((long long)timebase_diff <= 0 || XINTFRAC == 0) {
        return -1.0;
    }

    delta_time = HTIC2SEC(timebase_diff);

    user_diff = (double)(curr_stats.ucpu_time - prev_stats.ucpu_time);
    sys_diff  = (double)(curr_stats.scpu_time - prev_stats.scpu_time);

    prev_stats = curr_stats;
    prev_timebase = curr_timebase;

    double cpu_load = (user_diff + sys_diff) / delta_time;

    return (jdouble)cpu_load;



}

JNIEXPORT jdouble JNICALL
Java_com_sun_management_internal_OperatingSystemImpl_getSingleCpuLoad0
(JNIEnv *env, jobject dummy, jint cpu_number)
{
    return -1.0;
}

JNIEXPORT jlong JNICALL
Java_com_sun_management_internal_OperatingSystemImpl_getHostTotalCpuTicks0
(JNIEnv *env, jobject mbean)
{
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_sun_management_internal_OperatingSystemImpl_getHostConfiguredCpuCount0
(JNIEnv *env, jobject mbean)
{
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_sun_management_internal_OperatingSystemImpl_getHostOnlineCpuCount0
(JNIEnv *env, jobject mbean)
{
    return -1;
}
