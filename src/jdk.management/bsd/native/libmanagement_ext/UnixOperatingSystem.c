/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "com_sun_management_internal_OperatingSystemImpl.h"

#include <sys/resource.h>
#include <sys/types.h>
#include <sys/sysctl.h>
#include <sys/time.h>
#if !defined(__NetBSD__)
#include <sys/user.h>
#endif
#include <unistd.h>

#include "jvm.h"

JNIEXPORT jdouble JNICALL
Java_com_sun_management_internal_OperatingSystemImpl_getCpuLoad0
(JNIEnv *env, jobject dummy)
{
#ifdef __FreeBSD__
    /* This is based on the MacOS X implementation */

    static jlong last_used  = 0;
    static jlong last_total = 0;

    /* Load CPU times */
    long cp_time[CPUSTATES];
    size_t len = sizeof(cp_time);
    if (sysctlbyname("kern.cp_time", &cp_time, &len, NULL, 0) == -1) {
        return -1.;
    }

    jlong used  = cp_time[CP_USER] + cp_time[CP_NICE] + cp_time[CP_SYS] + cp_time[CP_INTR];
    jlong total = used + cp_time[CP_IDLE];

    if (last_used == 0 || last_total == 0) {
        // First call, just set the last values
        last_used  = used;
        last_total = total;
        // return 0 since we have no data, not -1 which indicates error
        return 0.;
    }

    jlong used_delta  = used - last_used;
    jlong total_delta = total - last_total;

    jdouble cpu = (jdouble) used_delta / total_delta;

    last_used  = used;
    last_total = total;

    return cpu;
#else
    // Not implemented yet
    return -1.;
#endif
}


#define TIME_VALUE_TO_TIMEVAL(a, r) do {  \
     (r)->tv_sec = (a)->seconds;          \
     (r)->tv_usec = (a)->microseconds;    \
} while (0)


#define TIME_VALUE_TO_MICROSECONDS(TV) \
     ((TV).tv_sec * 1000 * 1000 + (TV).tv_usec)


JNIEXPORT jdouble JNICALL
Java_com_sun_management_internal_OperatingSystemImpl_getProcessCpuLoad0
(JNIEnv *env, jobject dummy)
{
#ifdef __FreeBSD__
    /* This is based on the MacOS X implementation */

    static jlong last_task_time = 0;
    static jlong last_time      = 0;

    struct timeval now;
    struct kinfo_proc kp;
    int mib[4];
    size_t len = sizeof(struct kinfo_proc);

    mib[0] = CTL_KERN;
    mib[1] = KERN_PROC;
    mib[2] = KERN_PROC_PID;
    mib[3] = getpid();

    if (sysctl(mib, 4, &kp, &len, NULL, 0) == -1) {
        return -1.;
    }

    if (gettimeofday(&now, NULL) == -1) {
        return -1.;
    }

    jint ncpus      = JVM_ActiveProcessorCount();
    jlong time      = TIME_VALUE_TO_MICROSECONDS(now) * ncpus;
    jlong task_time = TIME_VALUE_TO_MICROSECONDS(kp.ki_rusage.ru_utime) +
                      TIME_VALUE_TO_MICROSECONDS(kp.ki_rusage.ru_stime);

    if ((last_task_time == 0) || (last_time == 0)) {
        // First call, just set the last values.
        last_task_time = task_time;
        last_time      = time;
        // return 0 since we have no data, not -1 which indicates error
        return 0.;
    }

    jlong task_time_delta = task_time - last_task_time;
    jlong time_delta      = time - last_time;
    if (time_delta == 0) {
        return -1.;
    }

    jdouble cpu = (jdouble) task_time_delta / time_delta;

    last_task_time = task_time;
    last_time      = time;

    return cpu;
#else
    // Not implemented yet
    return -1.;
#endif
}

JNIEXPORT jdouble JNICALL
Java_com_sun_management_internal_OperatingSystemImpl_getSingleCpuLoad0
(JNIEnv *env, jobject dummy, jint cpu_number)
{
    // Not implemented yet
    return -1.0;
}

JNIEXPORT jlong JNICALL
Java_com_sun_management_internal_OperatingSystemImpl_getHostTotalCpuTicks0
(JNIEnv *env, jobject mbean)
{
    return -1.0;
}

JNIEXPORT jint JNICALL
Java_com_sun_management_internal_OperatingSystemImpl_getHostConfiguredCpuCount0
(JNIEnv *env, jobject mbean)
{
#ifdef __FreeBSD__
    return JVM_ActiveProcessorCount();
#else
    // Not implemented yet
    return -1;
#endif
}
