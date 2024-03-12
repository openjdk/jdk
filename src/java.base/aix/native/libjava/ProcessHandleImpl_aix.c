/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "ProcessHandleImpl_unix.h"

#include <sys/procfs.h>
#include <procinfo.h>

/*
 * Implementation of native ProcessHandleImpl functions for AIX.
 * See ProcessHandleImpl_unix.c for more details.
 */

void os_initNative(JNIEnv *env, jclass clazz) {}

/*
 * Return pids of active processes, and optionally parent pids and
 * start times for each process.
 * For a specific non-zero pid, only the direct children are returned.
 * If the pid is zero, all active processes are returned.
 * Use getprocs64 to accumulate any process following the rules above.
 * The resulting pids are stored into an array of longs named jarray.
 * The number of pids is returned if they all fit.
 * If the parentArray is non-null, store also the parent pid.
 * In this case the parentArray must have the same length as the result pid array.
 * Of course in the case of a given non-zero pid all entries in the parentArray
 * will contain this pid, so this array does only make sense in the case of a given
 * zero pid.
 * If the jstimesArray is non-null, store also the start time of the pid.
 * In this case the jstimesArray must have the same length as the result pid array.
 * If the array(s) (is|are) too short, excess pids are not stored and
 * the desired length is returned.
 */
jint os_getChildren(JNIEnv *env, jlong jpid, jlongArray jarray,
                    jlongArray jparentArray, jlongArray jstimesArray) {
    pid_t pid = (pid_t) jpid;
    jlong* pids = NULL;
    jlong* ppids = NULL;
    jlong* stimes = NULL;
    jsize parentArraySize = 0;
    jsize arraySize = 0;
    jsize stimesSize = 0;
    jsize count = 0;

    arraySize = (*env)->GetArrayLength(env, jarray);
    JNU_CHECK_EXCEPTION_RETURN(env, -1);
    if (jparentArray != NULL) {
        parentArraySize = (*env)->GetArrayLength(env, jparentArray);
        JNU_CHECK_EXCEPTION_RETURN(env, -1);

        if (arraySize != parentArraySize) {
            JNU_ThrowIllegalArgumentException(env, "array sizes not equal");
            return 0;
        }
    }
    if (jstimesArray != NULL) {
        stimesSize = (*env)->GetArrayLength(env, jstimesArray);
        JNU_CHECK_EXCEPTION_RETURN(env, -1);

        if (arraySize != stimesSize) {
            JNU_ThrowIllegalArgumentException(env, "array sizes not equal");
            return 0;
        }
    }

    const int chunk = 100;
    struct procentry64 ProcessBuffer[chunk];
    pid_t idxptr = 0;
    int i, num = 0;

    do { // Block to break out of on Exception
        pids = (*env)->GetLongArrayElements(env, jarray, NULL);
        if (pids == NULL) {
            break;
        }
        if (jparentArray != NULL) {
            ppids  = (*env)->GetLongArrayElements(env, jparentArray, NULL);
            if (ppids == NULL) {
                break;
            }
        }
        if (jstimesArray != NULL) {
            stimes  = (*env)->GetLongArrayElements(env, jstimesArray, NULL);
            if (stimes == NULL) {
                break;
            }
        }

        while ((num = getprocs64(ProcessBuffer, sizeof(struct procentry64), NULL,
                                 sizeof(struct fdsinfo64), &idxptr, chunk)) != -1) {
            for (i = 0; i < num; i++) {
                pid_t childpid = (pid_t) ProcessBuffer[i].pi_pid;
                pid_t ppid = (pid_t) ProcessBuffer[i].pi_ppid;

                // Get the parent pid, and start time
                if (pid == 0 || ppid == pid) {
                    if (count < arraySize) {
                        // Only store if it fits
                        pids[count] = (jlong) childpid;

                        if (ppids != NULL) {
                            // Store the parentPid
                            ppids[count] = (jlong) ppid;
                        }
                        if (stimes != NULL) {
                            // Store the process start time
                            stimes[count] = ((jlong) ProcessBuffer[i].pi_start) * 1000;;
                        }
                    }
                    count++; // Count to tabulate size needed
                }
            }
            if (num < chunk) {
                break;
            }
        }
    } while (0);

    if (pids != NULL) {
        (*env)->ReleaseLongArrayElements(env, jarray, pids, 0);
    }
    if (ppids != NULL) {
        (*env)->ReleaseLongArrayElements(env, jparentArray, ppids, 0);
    }
    if (stimes != NULL) {
        (*env)->ReleaseLongArrayElements(env, jstimesArray, stimes, 0);
    }

    if (num == -1) {
        JNU_ThrowByNameWithLastError(env,
            "java/lang/RuntimeException", "Unable to retrieve Process info");
        return -1;
    }

    // If more pids than array had size for; count will be greater than array size
    return count;
}

pid_t os_getParentPidAndTimings(JNIEnv *env, pid_t pid, jlong *total, jlong *start) {
    return unix_getParentPidAndTimings(env, pid, total, start);
}

void os_getCmdlineAndUserInfo(JNIEnv *env, jobject jinfo, pid_t pid) {
    unix_getCmdlineAndUserInfo(env, jinfo, pid);
}
