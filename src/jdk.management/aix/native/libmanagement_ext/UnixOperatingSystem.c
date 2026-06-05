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
#include <libperfstat.h>
#include <pthread.h>
#include <stdlib.h>
#include <time.h>
#include "com_sun_management_internal_OperatingSystemImpl.h"

static struct perfMetrics{
    unsigned long long timebase;
    perfstat_process_t stats;
    perfstat_cpu_total_t cpu_total;
} counters;

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;

int perfInit() {
    static int initialized = 0;
    if (!initialized) {

        perfstat_id_t id;
        counters.stats = (perfstat_process_t){0};
        counters.timebase = 0;
        int rc = perfstat_cpu_total(NULL, &counters.cpu_total, sizeof(perfstat_cpu_total_t), 1);
        if (rc < 0) {
            return -1;
        }
        rc = perfstat_process(&id, &counters.stats, sizeof(perfstat_process_t), 1);
        if (rc < 0) {
            return -1;
        }
        counters.timebase = counters.stats.last_timebase;
        initialized = 1;
    }
    return initialized ? 0 : -1;
}

JNIEXPORT jdouble JNICALL
Java_com_sun_management_internal_OperatingSystemImpl_getCpuLoad0
(JNIEnv *env, jobject dummy)
{
    double load = -1.0;
    pthread_mutex_lock(&lock);
    if (perfInit() == 0) {
        int ret;
        perfstat_cpu_total_t cpu_total;
        ret = perfstat_cpu_total(NULL, &cpu_total, sizeof(perfstat_cpu_total_t), 1);
        if (ret < 0) {
            return -1.0;
        }
        long long user_diff = cpu_total.user - counters.cpu_total.user;
        long long sys_diff = cpu_total.sys - counters.cpu_total.sys;
        long long idle_diff = cpu_total.idle - counters.cpu_total.idle;
        long long wait_diff = cpu_total.wait - counters.cpu_total.wait;
        long long total = user_diff + sys_diff + idle_diff + wait_diff;
        if (total < (user_diff + sys_diff)) {
            total = user_diff + sys_diff;
        }
        if (total == 0) {
            load = 0.0;
        } else {
            load = (double)(user_diff + sys_diff) / total;
            load = MAX(load, 0.0);
            load = MIN(load, 1.0);
        }
        counters.cpu_total = cpu_total;
    }
    pthread_mutex_unlock(&lock);
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
    double cpu_load = -1.0;
    pthread_mutex_lock(&lock);
    if (perfInit() == 0) {
        int ret;
        ret = perfstat_process(&id, &curr_stats, sizeof(perfstat_process_t), 1);
        if (ret < 0) {
            return -1.0;
        }
        curr_timebase = curr_stats.last_timebase;
        timebase_diff = curr_timebase - counters.timebase;
        if ((long long)timebase_diff < 0 || XINTFRAC == 0) {
            return -1.0;
        }
        delta_time = HTIC2NANOSEC(timebase_diff) / 1000000000.0;
        user_diff = (double)(curr_stats.ucpu_time - counters.stats.ucpu_time);
        sys_diff  = (double)(curr_stats.scpu_time - counters.stats.scpu_time);
        counters.stats = curr_stats;
        counters.timebase = curr_timebase;
        if (delta_time == 0) {
            cpu_load = 0.0;
        } else {
            cpu_load = (user_diff + sys_diff) / delta_time;
            cpu_load = MAX(cpu_load, 0.0);
            cpu_load = MIN(cpu_load, 1.0);
        }
    }
    pthread_mutex_unlock(&lock);
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
