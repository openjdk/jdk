/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "jni_util.h"
#include "jlong.h"
#include "jvm.h"
#include "management.h"
#include "com_sun_management_OperatingSystem.h"

#include <errno.h>
#include <stdlib.h>

typedef unsigned __int32 juint;
typedef unsigned __int64 julong;

static void set_low(jlong* value, jint low) {
    *value &= (jlong)0xffffffff << 32;
    *value |= (jlong)(julong)(juint)low;
}

static void set_high(jlong* value, jint high) {
    *value &= (jlong)(julong)(juint)0xffffffff;
    *value |= (jlong)high       << 32;
}

static jlong jlong_from(jint h, jint l) {
  jlong result = 0; // initialization to avoid warning
  set_high(&result, h);
  set_low(&result,  l);
  return result;
}

// From psapi.h
typedef struct _PROCESS_MEMORY_COUNTERS {
    DWORD cb;
    DWORD PageFaultCount;
    SIZE_T PeakWorkingSetSize;
    SIZE_T WorkingSetSize;
    SIZE_T QuotaPeakPagedPoolUsage;
    SIZE_T QuotaPagedPoolUsage;
    SIZE_T QuotaPeakNonPagedPoolUsage;
    SIZE_T QuotaNonPagedPoolUsage;
    SIZE_T PagefileUsage;
    SIZE_T PeakPagefileUsage;
} PROCESS_MEMORY_COUNTERS;

static HINSTANCE hInstPsapi = NULL;
typedef BOOL (WINAPI *LPFNGETPROCESSMEMORYINFO)(HANDLE, PROCESS_MEMORY_COUNTERS*, DWORD);

static jboolean is_nt = JNI_FALSE;
static HANDLE main_process;

JNIEXPORT void JNICALL
Java_com_sun_management_OperatingSystem_initialize
  (JNIEnv *env, jclass cls)
{
    OSVERSIONINFO oi;
    oi.dwOSVersionInfoSize = sizeof(OSVERSIONINFO);
    GetVersionEx(&oi);
    switch(oi.dwPlatformId) {
        case VER_PLATFORM_WIN32_WINDOWS: is_nt = JNI_FALSE; break;
        case VER_PLATFORM_WIN32_NT:      is_nt = JNI_TRUE;  break;
        default:
            throw_internal_error(env, "Unsupported Platform");
            return;
    }

    main_process = GetCurrentProcess();
}

JNIEXPORT jlong JNICALL
Java_com_sun_management_OperatingSystem_getCommittedVirtualMemorySize0
  (JNIEnv *env, jobject mbean)
{

    /*
     * In bytes.  NT/2000/XP only - using GetProcessMemoryInfo from psapi.dll
     */
    static LPFNGETPROCESSMEMORYINFO lpfnGetProcessMemoryInfo = NULL;
    static volatile jboolean psapi_inited = JNI_FALSE;
    PROCESS_MEMORY_COUNTERS pmc;

    if (!is_nt) return -1;

    if (!psapi_inited) {
        psapi_inited = JNI_TRUE;
        if ((hInstPsapi = LoadLibrary("PSAPI.DLL")) == NULL) return -1;
        if ((lpfnGetProcessMemoryInfo = (LPFNGETPROCESSMEMORYINFO)
               GetProcAddress( hInstPsapi, "GetProcessMemoryInfo")) == NULL) {
            FreeLibrary(hInstPsapi);
            return -1;
        }
    }

    if (lpfnGetProcessMemoryInfo == NULL) return -1;

    lpfnGetProcessMemoryInfo(main_process, &pmc,
                             sizeof(PROCESS_MEMORY_COUNTERS));
    return (jlong) pmc.PagefileUsage;
}

JNIEXPORT jlong JNICALL
Java_com_sun_management_OperatingSystem_getTotalSwapSpaceSize
  (JNIEnv *env, jobject mbean)
{
    MEMORYSTATUS ms;
    GlobalMemoryStatus(&ms);
    return (jlong)ms.dwTotalPageFile;
}

JNIEXPORT jlong JNICALL
Java_com_sun_management_OperatingSystem_getFreeSwapSpaceSize
  (JNIEnv *env, jobject mbean)
{
    MEMORYSTATUS ms;
    GlobalMemoryStatus(&ms);
    return (jlong)ms.dwAvailPageFile;
}

JNIEXPORT jlong JNICALL
Java_com_sun_management_OperatingSystem_getProcessCpuTime
  (JNIEnv *env, jobject mbean)
{

    FILETIME process_creation_time, process_exit_time,
             process_user_time, process_kernel_time;

    // Windows NT only
    if (is_nt) {
        // Using static variables declared above
        // Units are 100-ns intervals.  Convert to ns.
        GetProcessTimes(main_process, &process_creation_time,
                        &process_exit_time,
                        &process_kernel_time, &process_user_time);
        return (jlong_from(process_user_time.dwHighDateTime,
                           process_user_time.dwLowDateTime) +
               jlong_from(process_kernel_time.dwHighDateTime,
                           process_kernel_time.dwLowDateTime)) * 100;
    } else {
        return -1;
    }
}

JNIEXPORT jlong JNICALL
Java_com_sun_management_OperatingSystem_getFreePhysicalMemorySize
  (JNIEnv *env, jobject mbean)
{
    MEMORYSTATUS ms;
    GlobalMemoryStatus(&ms);
    return (jlong) ms.dwAvailPhys;
}

JNIEXPORT jlong JNICALL
Java_com_sun_management_OperatingSystem_getTotalPhysicalMemorySize
  (JNIEnv *env, jobject mbean)
{
    MEMORYSTATUS ms;
    // also returns dwAvailPhys (free physical memory bytes),
    // dwTotalVirtual, dwAvailVirtual,
    // dwMemoryLoad (% of memory in use)
    GlobalMemoryStatus(&ms);
    return ms.dwTotalPhys;
}
