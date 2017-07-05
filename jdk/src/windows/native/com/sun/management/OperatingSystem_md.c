/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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

#include <psapi.h>
#include <errno.h>
#include <stdlib.h>

#include <malloc.h>
#pragma warning (push,0)
#include <windows.h>
#pragma warning (pop)
#include <stdio.h>
#include <time.h>
#include <stdint.h>
#include <assert.h>

/* Disable warnings due to broken header files from Microsoft... */
#pragma warning(push, 3)
#include <pdh.h>
#include <pdhmsg.h>
#include <process.h>
#pragma warning(pop)

typedef unsigned __int32 juint;
typedef unsigned __int64 julong;

typedef enum boolean_values { false=0, true=1};

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

static HANDLE main_process;

int perfiInit(void);

JNIEXPORT void JNICALL
Java_com_sun_management_OperatingSystem_initialize
  (JNIEnv *env, jclass cls)
{
    main_process = GetCurrentProcess();
     perfiInit();
}

JNIEXPORT jlong JNICALL
Java_com_sun_management_OperatingSystem_getCommittedVirtualMemorySize0
  (JNIEnv *env, jobject mbean)
{
    PROCESS_MEMORY_COUNTERS pmc;
    if (GetProcessMemoryInfo(main_process, &pmc, sizeof(PROCESS_MEMORY_COUNTERS)) == 0) {
        return (jlong)-1L;
    } else {
        return (jlong) pmc.PagefileUsage;
    }
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

    // Using static variables declared above
    // Units are 100-ns intervals.  Convert to ns.
    GetProcessTimes(main_process, &process_creation_time,
                    &process_exit_time,
                    &process_kernel_time, &process_user_time);
    return (jlong_from(process_user_time.dwHighDateTime,
                        process_user_time.dwLowDateTime) +
            jlong_from(process_kernel_time.dwHighDateTime,
                        process_kernel_time.dwLowDateTime)) * 100;
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

// Seems WinXP PDH returns PDH_MORE_DATA whenever we send in a NULL buffer.
// Let's just ignore it, since we make sure we have enough buffer anyway.
static int
pdh_fail(PDH_STATUS pdhStat) {
    return pdhStat != ERROR_SUCCESS && pdhStat != PDH_MORE_DATA;
}

// INFO: Using PDH APIs Correctly in a Localized Language (Q287159)
//       http://support.microsoft.com/default.aspx?scid=kb;EN-US;q287159
// The index value for the base system counters and objects like processor,
// process, thread, memory, and so forth are always the same irrespective
// of the localized version of the operating system or service pack installed.
#define PDH_PROCESSOR_IDX        ((DWORD) 238)
#define PDH_PROCESSOR_TIME_IDX        ((DWORD)   6)
#define PDH_PRIV_PROCESSOR_TIME_IDX ((DWORD) 144)
#define PDH_PROCESS_IDX            ((DWORD) 230)
#define PDH_ID_PROCESS_IDX        ((DWORD) 784)
#define PDH_CONTEXT_SWITCH_RATE_IDX ((DWORD) 146)
#define PDH_SYSTEM_IDX            ((DWORD)   2)
#define PDH_VIRTUAL_BYTES_IDX        ((DWORD) 174)

typedef PDH_STATUS (WINAPI *PdhAddCounterFunc)(
                           HQUERY      hQuery,
                           LPCSTR      szFullCounterPath,
                           DWORD       dwUserData,
                           HCOUNTER    *phCounter
                           );
typedef PDH_STATUS (WINAPI *PdhOpenQueryFunc)(
                          LPCWSTR     szDataSource,
                          DWORD       dwUserData,
                          HQUERY      *phQuery
                          );
typedef DWORD (WINAPI *PdhCloseQueryFunc)(
                      HQUERY      hQuery
                      );
typedef PDH_STATUS (WINAPI *PdhCollectQueryDataFunc)(
                             HQUERY      hQuery
                             );
typedef DWORD (WINAPI *PdhGetFormattedCounterValueFunc)(
                            HCOUNTER                hCounter,
                            DWORD                   dwFormat,
                            LPDWORD                 lpdwType,
                            PPDH_FMT_COUNTERVALUE   pValue
                            );
typedef PDH_STATUS (WINAPI *PdhEnumObjectItemsFunc)(
                            LPCTSTR    szDataSource,
                            LPCTSTR    szMachineName,
                            LPCTSTR    szObjectName,
                            LPTSTR     mszCounterList,
                            LPDWORD    pcchCounterListLength,
                            LPTSTR     mszInstanceList,
                            LPDWORD    pcchInstanceListLength,
                            DWORD      dwDetailLevel,
                            DWORD      dwFlags
                            );
typedef PDH_STATUS (WINAPI *PdhRemoveCounterFunc)(
                          HCOUNTER  hCounter
                          );
typedef PDH_STATUS (WINAPI *PdhLookupPerfNameByIndexFunc)(
                              LPCSTR  szMachineName,
                              DWORD   dwNameIndex,
                              LPSTR   szNameBuffer,
                              LPDWORD pcchNameBufferSize
                              );
typedef PDH_STATUS (WINAPI *PdhMakeCounterPathFunc)(
                            PDH_COUNTER_PATH_ELEMENTS *pCounterPathElements,
                            LPTSTR szFullPathBuffer,
                            LPDWORD pcchBufferSize,
                            DWORD dwFlags
                            );

static PdhAddCounterFunc PdhAddCounter_i;
static PdhOpenQueryFunc PdhOpenQuery_i;
static PdhCloseQueryFunc PdhCloseQuery_i;
static PdhCollectQueryDataFunc PdhCollectQueryData_i;
static PdhGetFormattedCounterValueFunc PdhGetFormattedCounterValue_i;
static PdhEnumObjectItemsFunc PdhEnumObjectItems_i;
static PdhRemoveCounterFunc PdhRemoveCounter_i;
static PdhLookupPerfNameByIndexFunc PdhLookupPerfNameByIndex_i;
static PdhMakeCounterPathFunc PdhMakeCounterPath_i;

static HANDLE thisProcess;
static double cpuFactor;
static DWORD  num_cpus;

#define FT2JLONG(X)  ((((jlong)X.dwHighDateTime) << 32) | ((jlong)X.dwLowDateTime))
#define COUNTER_BUF_SIZE 256
// Min time between query updates.
#define MIN_UPDATE_INTERVAL 500
#define CONFIG_SUCCESSFUL 0

/**
 * Struct for PDH queries.
 */
typedef struct {
    HQUERY      query;
    uint64_t      lastUpdate; // Last time query was updated (current millis).
} UpdateQueryS, *UpdateQueryP;

/**
 * Struct for the processor load counters.
 */
typedef struct {
    UpdateQueryS      query;
    HCOUNTER*      counters;
    int          noOfCounters;
} MultipleCounterQueryS, *MultipleCounterQueryP;

/**
 * Struct for the jvm process load counter.
 */
typedef struct {
    UpdateQueryS      query;
    HCOUNTER      counter;
} SingleCounterQueryS, *SingleCounterQueryP;

static char* getProcessPDHHeader(void);

/**
 * Currently available counters.
 */
static SingleCounterQueryS cntCtxtSwitchRate;
static SingleCounterQueryS cntVirtualSize;
static SingleCounterQueryS cntProcLoad;
static SingleCounterQueryS cntProcSystemLoad;
static MultipleCounterQueryS multiCounterCPULoad;

static CRITICAL_SECTION processHeaderLock;
static CRITICAL_SECTION initializationLock;

/**
 * Initialize the perf module at startup.
 */
int
perfiInit(void)
{
    InitializeCriticalSection(&processHeaderLock);
    InitializeCriticalSection(&initializationLock);
    return 0;
}

/**
 * Dynamically sets up function pointers to the PDH library.
 *
 * @return CONFIG_SUCCESSFUL on success, negative on failure.
 */
static int
get_functions(HMODULE h, char *ebuf, size_t elen) {
    // The 'A' at the end means the ANSI (not the UNICODE) vesions of the methods
    PdhAddCounter_i         = (PdhAddCounterFunc)GetProcAddress(h, "PdhAddCounterA");
    PdhOpenQuery_i         = (PdhOpenQueryFunc)GetProcAddress(h, "PdhOpenQueryA");
    PdhCloseQuery_i         = (PdhCloseQueryFunc)GetProcAddress(h, "PdhCloseQuery");
    PdhCollectQueryData_i     = (PdhCollectQueryDataFunc)GetProcAddress(h, "PdhCollectQueryData");
    PdhGetFormattedCounterValue_i = (PdhGetFormattedCounterValueFunc)GetProcAddress(h, "PdhGetFormattedCounterValue");
    PdhEnumObjectItems_i         = (PdhEnumObjectItemsFunc)GetProcAddress(h, "PdhEnumObjectItemsA");
    PdhRemoveCounter_i         = (PdhRemoveCounterFunc)GetProcAddress(h, "PdhRemoveCounter");
    PdhLookupPerfNameByIndex_i     = (PdhLookupPerfNameByIndexFunc)GetProcAddress(h, "PdhLookupPerfNameByIndexA");
    PdhMakeCounterPath_i         = (PdhMakeCounterPathFunc)GetProcAddress(h, "PdhMakeCounterPathA");

    if (PdhAddCounter_i == NULL || PdhOpenQuery_i == NULL ||
    PdhCloseQuery_i == NULL || PdhCollectQueryData_i == NULL ||
    PdhGetFormattedCounterValue_i == NULL || PdhEnumObjectItems_i == NULL ||
    PdhRemoveCounter_i == NULL || PdhLookupPerfNameByIndex_i == NULL || PdhMakeCounterPath_i == NULL)
    {
        _snprintf(ebuf, elen, "Required method could not be found.");
        return -1;
    }
    return CONFIG_SUCCESSFUL;
}

/**
 * Returns the counter value as a double for the specified query.
 * Will collect the query data and update the counter values as necessary.
 *
 * @param query       the query to update (if needed).
 * @param c          the counter to read.
 * @param value       where to store the formatted value.
 * @param format      the format to use (i.e. PDH_FMT_DOUBLE, PDH_FMT_LONG etc)
 * @return            CONFIG_SUCCESSFUL if no error
 *                    -1 if PdhCollectQueryData fails
 *                    -2 if PdhGetFormattedCounterValue fails
 */
static int
getPerformanceData(UpdateQueryP query, HCOUNTER c, PDH_FMT_COUNTERVALUE* value, DWORD format) {
    clock_t now;
    now = clock();

    // Need to limit how often we update the query
    // to mimise the heisenberg effect.
    // (PDH behaves erratically if the counters are
    // queried too often, especially counters that
    // store and use values from two consecutive updates,
    // like cpu load.)
    if (now - query->lastUpdate > MIN_UPDATE_INTERVAL) {
        if (PdhCollectQueryData_i(query->query) != ERROR_SUCCESS) {
            return -1;
        }
        query->lastUpdate = now;
    }

    if (PdhGetFormattedCounterValue_i(c, format, NULL, value) != ERROR_SUCCESS) {
        return -2;
    }
    return CONFIG_SUCCESSFUL;
}

/**
 * Places the resolved counter name of the counter at the specified index in the
 * supplied buffer. There must be enough space in the buffer to hold the counter name.
 *
 * @param index   the counter index as specified in the registry.
 * @param buf     the buffer in which to place the counter name.
 * @param size      the size of the counter name buffer.
 * @param ebuf    the error message buffer.
 * @param elen    the length of the error buffer.
 * @return        CONFIG_SUCCESSFUL if successful, negative on failure.
 */
static int
find_name(DWORD index, char *buf, DWORD size) {
    PDH_STATUS res;

    if ((res = PdhLookupPerfNameByIndex_i(NULL, index, buf, &size)) != ERROR_SUCCESS) {

        /* printf("Could not open counter %d: error=0x%08x", index, res); */
        /* if (res == PDH_CSTATUS_NO_MACHINE) { */
        /*      printf("User probably does not have sufficient privileges to use"); */
        /*      printf("performance counters. If you are running on Windows 2003"); */
        /*      printf("or Windows Vista, make sure the user is in the"); */
        /*      printf("Performance Logs user group."); */
        /* } */
        return -1;
    }

    if (size == 0) {
        /* printf("Failed to get counter name for %d: empty string", index); */
        return -1;
    }

    // windows vista does not null-terminate the string (allthough the docs says it will)
    buf[size - 1] = '\0';
    return CONFIG_SUCCESSFUL;
}

/**
 * Sets up the supplied SingleCounterQuery to listen for the specified counter.
 * initPDH() must have been run prior to calling this function!
 *
 * @param counterQuery   the counter query to set up.
 * @param counterString  the string specifying the path to the counter.
 * @param ebuf           the error buffer.
 * @param elen           the length of the error buffer.
 * @returns              CONFIG_SUCCESSFUL if successful, negative on failure.
 */
static int
initSingleCounterQuery(SingleCounterQueryP counterQuery, char *counterString) {
    if (PdhOpenQuery_i(NULL, 0, &counterQuery->query.query) != ERROR_SUCCESS) {
        /* printf("Could not open query for %s", counterString); */
        return -1;
    }
    if (PdhAddCounter_i(counterQuery->query.query, counterString, 0, &counterQuery->counter) != ERROR_SUCCESS) {
        /* printf("Could not add counter %s for query", counterString); */
        if (counterQuery->counter != NULL) {
            PdhRemoveCounter_i(counterQuery->counter);
        }
        if (counterQuery->query.query != NULL) {
            PdhCloseQuery_i(counterQuery->query.query);
        }
        memset(counterQuery, 0, sizeof(SingleCounterQueryS));
        return -1;
    }
    return CONFIG_SUCCESSFUL;
}

/**
 * Sets up the supplied SingleCounterQuery to listen for the time spent
 * by the HotSpot process.
 *
 * @param counterQuery   the counter query to set up as a process counter.
 * @param ebuf           the error buffer.
 * @param elen           the length of the error buffer.
 * @returns              CONFIG_SUCCESSFUL if successful, negative on failure.
 */
static int
initProcLoadCounter(void) {
    char time[COUNTER_BUF_SIZE];
    char counter[COUNTER_BUF_SIZE*2];

    if (find_name(PDH_PROCESSOR_TIME_IDX, time, sizeof(time)-1) < 0) {
        return -1;
    }
    _snprintf(counter, sizeof(counter)-1, "%s\\%s", getProcessPDHHeader(), time);
    return initSingleCounterQuery(&cntProcLoad, counter);
}

static int
initProcSystemLoadCounter(void) {
    char time[COUNTER_BUF_SIZE];
    char counter[COUNTER_BUF_SIZE*2];

    if (find_name(PDH_PRIV_PROCESSOR_TIME_IDX, time, sizeof(time)-1) < 0) {
        return -1;
    }
    _snprintf(counter, sizeof(counter)-1, "%s\\%s", getProcessPDHHeader(), time);
    return initSingleCounterQuery(&cntProcSystemLoad, counter);
}

/**
 * Sets up the supplied MultipleCounterQuery to check on the processors.
 * (Comment: Refactor and prettify as with the the SingleCounter queries
 * if more MultipleCounterQueries are discovered.)
 *
 * initPDH() must have been run prior to calling this function.
 *
 * @param multiQuery  a pointer to a MultipleCounterQueryS, will be filled in with
 *                    the necessary info to check the PDH processor counters.
 * @return            CONFIG_SUCCESSFUL if successful, negative on failure.
 */
static int
initProcessorCounters(void) {
    char          processor[COUNTER_BUF_SIZE]; //'Processor' == #238
    char          time[COUNTER_BUF_SIZE];      //'Time' == 6
    DWORD      c_size, i_size;
    HQUERY     tmpQuery;
    DWORD      i, p_count;
    BOOL          error;
    char         *instances, *tmp;
    PDH_STATUS pdhStat;

    c_size   = i_size = 0;
    tmpQuery = NULL;
    error    = false;

    // This __try / __except stuff is there since Windows 2000 beta (or so) sometimes triggered
    // an access violation when the user had insufficient privileges to use the performance
    // counters. This was previously guarded by a very ugly piece of code which disabled the
    // global trap handling in JRockit. Don't know if this really is needed anymore, but otoh,
    // if we keep it we don't crash on Win2k beta. /Ihse, 2005-05-30
    __try {
        if (find_name(PDH_PROCESSOR_IDX, processor, sizeof(processor)-1) < 0) {
            return -1;
        }
    } __except (EXCEPTION_EXECUTE_HANDLER) { // We'll catch all exceptions here.
        /* printf("User does not have sufficient privileges to use performance counters"); */
        return -1;
    }

    if (find_name(PDH_PROCESSOR_TIME_IDX, time, sizeof(time)-1) < 0) {
        return -1;
    }
    //ok, now we have enough to enumerate all processors.
    pdhStat = PdhEnumObjectItems_i (
                    NULL,                   // reserved
                    NULL,                   // local machine
                    processor,          // object to enumerate
                    NULL,              // pass in NULL buffers
                    &c_size,              // and 0 length to get
                    NULL,              // required size
                    &i_size,              // of the buffers in chars
                    PERF_DETAIL_WIZARD,     // counter detail level
                    0);
    if (pdh_fail(pdhStat)) {
        /* printf("could not enumerate processors (1) error=%d", pdhStat); */
        return -1;
    }

    // use calloc because windows vista does not null terminate the instance names (allthough the docs says it will)
    instances = calloc(i_size, 1);
    if (instances == NULL) {
        /* printf("could not allocate memory (1) %d bytes", i_size); */
        error = true;
        goto end;
    }

    c_size  = 0;
    pdhStat = PdhEnumObjectItems_i (
                    NULL,                   // reserved
                    NULL,                   // local machine
                    processor,              // object to enumerate
                    NULL,              // pass in NULL buffers
                    &c_size,              // and 0 length to get
                    instances,          // required size
                    &i_size,              // of the buffers in chars
                    PERF_DETAIL_WIZARD,     // counter detail level
                    0);

    if (pdh_fail(pdhStat)) {
        /* printf("could not enumerate processors (2) error=%d", pdhStat); */
        error = true;
        goto end;
    }
    //count perf count instances.
    for (p_count = 0, tmp = instances; *tmp != 0; tmp = &tmp[lstrlen(tmp)+1], p_count++);

    //is this correct for HT?
    assert(p_count == num_cpus+1);

    //ok, have number of perf counters.
    multiCounterCPULoad.counters = calloc(p_count, sizeof(HCOUNTER));
    if (multiCounterCPULoad.counters == NULL) {
        /* printf("could not allocate memory (2) count=%d", p_count); */
        error = true;
        goto end;
    }

    multiCounterCPULoad.noOfCounters = p_count;

    if (PdhOpenQuery_i(NULL, 0, &multiCounterCPULoad.query.query) != ERROR_SUCCESS) {
        /* printf("could not create query"); */
        error = true;
        goto end;
    }
    //now, fetch the counters.
    for (i = 0, tmp = instances; *tmp != '\0'; tmp = &tmp[lstrlen(tmp)+1], i++) {
    char counter[2*COUNTER_BUF_SIZE];

    _snprintf(counter, sizeof(counter)-1, "\\%s(%s)\\%s", processor, tmp, time);

    if (PdhAddCounter_i(multiCounterCPULoad.query.query, counter, 0, &multiCounterCPULoad.counters[i]) != ERROR_SUCCESS) {
            /* printf("error adding processor counter %s", counter); */
            error = true;
            goto end;
        }
    }

    free(instances);
    instances = NULL;

    // Query once to initialize the counters needing at least two queries
    // (like the % CPU usage) to calculate correctly.
    if (PdhCollectQueryData_i(multiCounterCPULoad.query.query) != ERROR_SUCCESS)
        error = true;

 end:
    if (instances != NULL) {
        free(instances);
    }
    if (tmpQuery != NULL) {
        PdhCloseQuery_i(tmpQuery);
    }
    if (error) {
        int i;

        if (multiCounterCPULoad.counters != NULL) {
            for (i = 0; i < multiCounterCPULoad.noOfCounters; i++) {
                if (multiCounterCPULoad.counters[i] != NULL) {
                    PdhRemoveCounter_i(multiCounterCPULoad.counters[i]);
                }
            }
            free(multiCounterCPULoad.counters[i]);
        }
        if (multiCounterCPULoad.query.query != NULL) {
            PdhCloseQuery_i(multiCounterCPULoad.query.query);
        }
        memset(&multiCounterCPULoad, 0, sizeof(MultipleCounterQueryS));
        return -1;
    }
    return CONFIG_SUCCESSFUL;
}

/**
 * Help function that initializes the PDH process header for the JRockit process.
 * (You should probably use getProcessPDHHeader() instead!)
 *
 * initPDH() must have been run prior to calling this function.
 *
 * @param ebuf the error buffer.
 * @param elen the length of the error buffer.
 *
 * @return the PDH instance description corresponding to the JVM process.
 */
static char*
initProcessPDHHeader(void) {
    static char hotspotheader[2*COUNTER_BUF_SIZE];

    char           counter[2*COUNTER_BUF_SIZE];
    char           processes[COUNTER_BUF_SIZE];   //'Process' == #230
    char           pid[COUNTER_BUF_SIZE];           //'ID Process' == 784
    char           module_name[MAX_PATH];
    PDH_STATUS  pdhStat;
    DWORD       c_size = 0, i_size = 0;
    HQUERY      tmpQuery = NULL;
    int           i, myPid = _getpid();
    BOOL           error = false;
    char          *instances, *tmp, *instance_name, *dot_pos;

    tmpQuery = NULL;
    myPid    = _getpid();
    error    = false;

    if (find_name(PDH_PROCESS_IDX, processes, sizeof(processes) - 1) < 0) {
        return NULL;
    }

    if (find_name(PDH_ID_PROCESS_IDX, pid, sizeof(pid) - 1) < 0) {
        return NULL;
    }
    //time is same.

    c_size = 0;
    i_size = 0;

    pdhStat = PdhEnumObjectItems_i (
                    NULL,                   // reserved
                    NULL,                   // local machine
                    processes,              // object to enumerate
                    NULL,                   // pass in NULL buffers
                    &c_size,              // and 0 length to get
                    NULL,              // required size
                    &i_size,              // of the buffers in chars
                    PERF_DETAIL_WIZARD,     // counter detail level
                    0);

    //ok, now we have enough to enumerate all processes
    if (pdh_fail(pdhStat)) {
        /* printf("Could not enumerate processes (1) error=%d", pdhStat); */
        return NULL;
    }

    // use calloc because windows vista does not null terminate the instance names (allthough the docs says it will)
    if ((instances = calloc(i_size, 1)) == NULL) {
        /* printf("Could not allocate memory %d bytes", i_size); */
        error = true;
        goto end;
    }

    c_size = 0;

    pdhStat = PdhEnumObjectItems_i (
                    NULL,                   // reserved
                    NULL,                   // local machine
                    processes,              // object to enumerate
                    NULL,              // pass in NULL buffers
                    &c_size,              // and 0 length to get
                    instances,          // required size
                    &i_size,              // of the buffers in chars
                    PERF_DETAIL_WIZARD,     // counter detail level
                    0);

    // ok, now we have enough to enumerate all processes
    if (pdh_fail(pdhStat)) {
        /* printf("Could not enumerate processes (2) error=%d", pdhStat); */
        error = true;
        goto end;
    }

    if (PdhOpenQuery_i(NULL, 0, &tmpQuery) != ERROR_SUCCESS) {
        /* printf("Could not create temporary query"); */
        error = true;
        goto end;
    }

    // Find our module name and use it to extract the instance name used by PDH
    if (GetModuleFileName(NULL, module_name, MAX_PATH) >= MAX_PATH-1) {
        /* printf("Module name truncated"); */
        error = true;
        goto end;
    }
    instance_name = strrchr(module_name, '\\'); //drop path
    instance_name++;                            //skip slash
    dot_pos = strchr(instance_name, '.');       //drop .exe
    dot_pos[0] = '\0';

    //now, fetch the counters.
    for (tmp = instances; *tmp != 0 && !error; tmp = &tmp[lstrlen(tmp)+1]) {
        HCOUNTER  hc = NULL;
        BOOL done = false;

        // Skip until we find our own process name
        if (strcmp(tmp, instance_name) != 0) {
            continue;
        }

        // iterate over all instance indexes and try to find our own pid
        for (i = 0; !done && !error; i++){
            PDH_STATUS res;
            _snprintf(counter, sizeof(counter)-1, "\\%s(%s#%d)\\%s", processes, tmp, i, pid);

            if (PdhAddCounter_i(tmpQuery, counter, 0, &hc) != ERROR_SUCCESS) {
                /* printf("Failed to create process id query"); */
                error = true;
                goto end;
            }

            res = PdhCollectQueryData_i(tmpQuery);

            if (res == PDH_INVALID_HANDLE) {
                /* printf("Failed to query process id"); */
                res = -1;
                done = true;
            } else if (res == PDH_NO_DATA) {
                done = true;
            } else {
                PDH_FMT_COUNTERVALUE cv;

                PdhGetFormattedCounterValue_i(hc, PDH_FMT_LONG, NULL, &cv);
               /*
                 * This check seems to be needed for Win2k SMP boxes, since
                 * they for some reason don't return PDH_NO_DATA for non existing
                 * counters.
                 */
                if (cv.CStatus != PDH_CSTATUS_VALID_DATA) {
                    done = true;
                } else if (cv.longValue == myPid) {
                    _snprintf(hotspotheader, sizeof(hotspotheader)-1, "\\%s(%s#%d)\0", processes, tmp, i);
                    PdhRemoveCounter_i(hc);
                    goto end;
                }
            }
            PdhRemoveCounter_i(hc);
        }
    }
 end:
    if (instances != NULL) {
        free(instances);
    }
    if (tmpQuery != NULL) {
        PdhCloseQuery_i(tmpQuery);
    }
    if (error) {
        return NULL;
    }
    return hotspotheader;
}

/**
 * Returns the PDH string prefix identifying the HotSpot process. Use this prefix when getting
 * counters from the PDH process object representing HotSpot.
 *
 * Note: this call may take some time to complete.
 *
 * @param ebuf error buffer.
 * @param elen error buffer length.
 *
 * @return the header to be used when retrieving PDH counters from the HotSpot process.
 * Will return NULL if the call failed.
 */
static char *
getProcessPDHHeader(void) {
    static char *processHeader = NULL;

    EnterCriticalSection(&processHeaderLock); {
        if (processHeader == NULL) {
            processHeader = initProcessPDHHeader();
        }
    } LeaveCriticalSection(&processHeaderLock);
    return processHeader;
}

int perfInit(void);

double
perfGetCPULoad(int which)
{
    PDH_FMT_COUNTERVALUE cv;
    HCOUNTER            c;

    if (perfInit() < 0) {
        // warn?
        return -1.0;
    }

    if (multiCounterCPULoad.query.query == NULL) {
        // warn?
        return -1.0;
    }

    if (which == -1) {
        c = multiCounterCPULoad.counters[multiCounterCPULoad.noOfCounters - 1];
    } else {
        if (which < multiCounterCPULoad.noOfCounters) {
            c = multiCounterCPULoad.counters[which];
        } else {
            return -1.0;
        }
    }
    if (getPerformanceData(&multiCounterCPULoad.query, c, &cv, PDH_FMT_DOUBLE ) == CONFIG_SUCCESSFUL) {
        return cv.doubleValue / 100;
    }
    return -1.0;
}

double
perfGetProcessLoad(void)
{
    PDH_FMT_COUNTERVALUE cv;

    if (perfInit() < 0) {
        // warn?
        return -1.0;
    }

    if (cntProcLoad.query.query == NULL) {
        // warn?
        return -1.0;
    }

    if (getPerformanceData(&cntProcLoad.query, cntProcLoad.counter, &cv, PDH_FMT_DOUBLE | PDH_FMT_NOCAP100) == CONFIG_SUCCESSFUL) {
        double d = cv.doubleValue / cpuFactor;
        d = min(1, d);
        d = max(0, d);
        return d;
    }
    return -1.0;
}

/**
 * Helper to initialize the PDH library. Loads the library and sets up the functions.
 * Note that once loaded, we will never unload the PDH library.
 *
 * @return  CONFIG_SUCCESSFUL if successful, negative on failure.
 */
int
perfInit(void) {
    static HMODULE    h;
    static BOOL        running, inited;

    int error;

    if (running) {
        return CONFIG_SUCCESSFUL;
    }

    error = CONFIG_SUCCESSFUL;

    // this is double checked locking again, but we try to bypass the worst by
    // implicit membar at end of lock.
    EnterCriticalSection(&initializationLock); {
        if (!inited) {
            char         buf[64] = "";
            SYSTEM_INFO si;

            // CMH. But windows will not care about our affinity when giving
            // us measurements. Need the real, raw num cpus.

            GetSystemInfo(&si);
            num_cpus  = si.dwNumberOfProcessors;
            // Initialize the denominator for the jvm load calculations
            cpuFactor = num_cpus * 100;

            /**
             * Do this dynamically, so we don't fail to start on systems without pdh.
             */
            if ((h = LoadLibrary("pdh.dll")) == NULL) {
                /* printf("Could not load pdh.dll (%d)", GetLastError()); */
                error = -2;
            } else if (get_functions(h, buf, sizeof(buf)) < 0) {
                FreeLibrary(h);
                h = NULL;
                error = -2;
               /* printf("Failed to init pdh functions: %s.\n", buf); */
            } else {
                if (initProcessorCounters() != 0) {
                    /* printf("Failed to init system load counters.\n"); */
                } else if (initProcLoadCounter() != 0) {
                    /* printf("Failed to init process load counter.\n"); */
                } else if (initProcSystemLoadCounter() != 0) {
                    /* printf("Failed to init process system load counter.\n"); */
                } else {
                    inited = true;
                }
            }
        }
    } LeaveCriticalSection(&initializationLock);

    if (inited && error == CONFIG_SUCCESSFUL) {
        running = true;
    }

    return error;
}

JNIEXPORT jdouble JNICALL
Java_com_sun_management_OperatingSystem_getSystemCpuLoad
(JNIEnv *env, jobject dummy)
{
    return perfGetCPULoad(-1);
}

JNIEXPORT jdouble JNICALL
Java_com_sun_management_OperatingSystem_getProcessCpuLoad
(JNIEnv *env, jobject dummy)
{
    return perfGetProcessLoad();
}
