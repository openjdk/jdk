/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "java_lang_ProcessHandleImpl.h"
#include "java_lang_ProcessHandleImpl_Info.h"

#include <stdio.h>
#include <errno.h>
#include <signal.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>

#include <sys/sysctl.h>

/**
 * Implementations of ProcessHandleImpl functions for MAC OS X;
 * are NOT common to all Unix variants.
 */

static void getStatInfo(JNIEnv *env, jobject jinfo, pid_t pid);
static void getCmdlineInfo(JNIEnv *env, jobject jinfo, pid_t pid);

/*
 * Common Unix function to lookup the uid and return the user name.
 */
extern jstring uidToUser(JNIEnv* env, uid_t uid);

/* Field id for jString 'command' in java.lang.ProcessHandle.Info */
static jfieldID ProcessHandleImpl_Info_commandID;

/* Field id for jString[] 'arguments' in java.lang.ProcessHandle.Info */
static jfieldID ProcessHandleImpl_Info_argumentsID;

/* Field id for jlong 'totalTime' in java.lang.ProcessHandle.Info */
static jfieldID ProcessHandleImpl_Info_totalTimeID;

/* Field id for jlong 'startTime' in java.lang.ProcessHandle.Info */
static jfieldID ProcessHandleImpl_Info_startTimeID;

/* Field id for jString 'user' in java.lang.ProcessHandleImpl.Info */
static jfieldID ProcessHandleImpl_Info_userID;

/* static value for clock ticks per second. */
static long clock_ticks_per_second;

/**************************************************************
 * Static method to initialize field IDs and the ticks per second rate.
 *
 * Class:     java_lang_ProcessHandleImpl_Info
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_java_lang_ProcessHandleImpl_00024Info_initIDs
  (JNIEnv *env, jclass clazz) {

    CHECK_NULL(ProcessHandleImpl_Info_commandID =
            (*env)->GetFieldID(env, clazz, "command", "Ljava/lang/String;"));
    CHECK_NULL(ProcessHandleImpl_Info_argumentsID =
            (*env)->GetFieldID(env, clazz, "arguments", "[Ljava/lang/String;"));
    CHECK_NULL(ProcessHandleImpl_Info_totalTimeID =
            (*env)->GetFieldID(env, clazz, "totalTime", "J"));
    CHECK_NULL(ProcessHandleImpl_Info_startTimeID =
            (*env)->GetFieldID(env, clazz, "startTime", "J"));
    CHECK_NULL(ProcessHandleImpl_Info_userID =
            (*env)->GetFieldID(env, clazz, "user", "Ljava/lang/String;"));
    clock_ticks_per_second = sysconf(_SC_CLK_TCK);
}

/*
 * Returns the parent pid of the requested pid.
 *
 * Class:     java_lang_ProcessHandleImpl
 * Method:    parent0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_java_lang_ProcessHandleImpl_parent0
(JNIEnv *env, jobject obj, jlong jpid) {
    pid_t pid = (pid_t) jpid;
    pid_t ppid = -1;

    if (pid == getpid()) {
        ppid = getppid();
    } else {
        const pid_t pid = (pid_t) jpid;
        struct kinfo_proc kp;
        size_t bufSize = sizeof kp;

        // Read the process info for the specific pid
        int mib[4] = {CTL_KERN, KERN_PROC, KERN_PROC_PID, pid};
        if (sysctl(mib, 4, &kp, &bufSize, NULL, 0) < 0) {
            JNU_ThrowByNameWithLastError(env,
                "java/lang/RuntimeException", "sysctl failed");
            return -1;
        }
        ppid = (bufSize > 0 && kp.kp_proc.p_pid == pid) ? kp.kp_eproc.e_ppid : -1;
    }
    return (jlong) ppid;
}

/*
 * Returns the children of the requested pid and optionally each parent.
 *
 * Class:     java_lang_ProcessHandleImpl
 * Method:    getProcessPids0
 * Signature: (J[J[J)I
 *
 * Use sysctl to accumulate any process whose parent pid is zero or matches.
 * The resulting pids are stored into the array of longs.
 * The number of pids is returned if they all fit.
 * If the parentArray is non-null, store the parent pid.
 * If the array is too short, excess pids are not stored and
 * the desired length is returned.
 */
JNIEXPORT jint JNICALL Java_java_lang_ProcessHandleImpl_getProcessPids0
(JNIEnv *env, jclass clazz, jlong jpid,
    jlongArray jarray, jlongArray jparentArray)
{
    size_t count = 0;
    jlong* pids = NULL;
    jlong* ppids = NULL;
    size_t parentArraySize = 0;
    size_t arraySize = 0;
    size_t bufSize = 0;
    pid_t pid = (pid_t) jpid;

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

    // Get buffer size needed to read all processes
    int mib[4] = {CTL_KERN, KERN_PROC, KERN_PROC_ALL, 0};
    if (sysctl(mib, 4, NULL, &bufSize, NULL, 0) < 0) {
        JNU_ThrowByNameWithLastError(env,
            "java/lang/RuntimeException", "sysctl failed");
        return -1;
    }

    // Allocate buffer big enough for all processes
    void *buffer = malloc(bufSize);
    if (buffer == NULL) {
        JNU_ThrowOutOfMemoryError(env, "malloc failed");
        return -1;
    }

    // Read process info for all processes
    if (sysctl(mib, 4, buffer, &bufSize, NULL, 0) < 0) {
        JNU_ThrowByNameWithLastError(env,
            "java/lang/RuntimeException", "sysctl failed");
        free(buffer);
        return -1;
    }

    do { // Block to break out of on Exception
        struct kinfo_proc *kp = (struct kinfo_proc *) buffer;
        unsigned long nentries = bufSize / sizeof (struct kinfo_proc);
        long i;

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

        // Process each entry in the buffer
        for (i = nentries; --i >= 0; ++kp) {
            if (pid == 0 || kp->kp_eproc.e_ppid == pid) {
                if (count < arraySize) {
                    // Only store if it fits
                    pids[count] = (jlong) kp->kp_proc.p_pid;
                    if (ppids != NULL) {
                        // Store the parentPid
                        ppids[count] = (jlong) kp->kp_eproc.e_ppid;
                    }
                }
                count++; // Count to tabulate size needed
            }
        }
    } while (0);

    if (pids != NULL) {
        (*env)->ReleaseLongArrayElements(env, jarray, pids, 0);
    }
    if (ppids != NULL) {
        (*env)->ReleaseLongArrayElements(env, jparentArray, ppids, 0);
    }

    free(buffer);
    // If more pids than array had size for; count will be greater than array size
    return count;
}

/**************************************************************
 * Implementation of ProcessHandleImpl_Info native methods.
 */

/*
 * Fill in the Info object from the OS information about the process.
 *
 * Class:     java_lang_ProcessHandleImpl
 * Method:    info0
 * Signature: (J)I
 */
JNIEXPORT void JNICALL Java_java_lang_ProcessHandleImpl_00024Info_info0
  (JNIEnv *env, jobject jinfo, jlong jpid) {
    pid_t pid = (pid_t) jpid;
    getStatInfo(env, jinfo, pid);
    getCmdlineInfo(env, jinfo, pid);
}

/**
 * Read /proc/<pid>/stat and fill in the fields of the Info object.
 * The executable name, plus the user, system, and start times are gathered.
 */
static void getStatInfo(JNIEnv *env, jobject jinfo, pid_t jpid) {
    jlong totalTime;                    // nanoseconds
    unsigned long long startTime;       // microseconds

    const pid_t pid = (pid_t) jpid;
    struct kinfo_proc kp;
    size_t bufSize = sizeof kp;

    // Read the process info for the specific pid
    int mib[4] = {CTL_KERN, KERN_PROC, KERN_PROC_PID, pid};

    if (sysctl(mib, 4, &kp, &bufSize, NULL, 0) < 0) {
        if (errno == EINVAL) {
            return;
        } else {
            JNU_ThrowByNameWithLastError(env,
                "java/lang/RuntimeException", "sysctl failed");
        }
        return;
    }

    // Convert the UID to the username
    jstring name = NULL;
    CHECK_NULL((name = uidToUser(env, kp.kp_eproc.e_ucred.cr_uid)));
    (*env)->SetObjectField(env, jinfo, ProcessHandleImpl_Info_userID, name);
    JNU_CHECK_EXCEPTION(env);

    startTime = kp.kp_proc.p_starttime.tv_sec * 1000 +
                kp.kp_proc.p_starttime.tv_usec / 1000;

    (*env)->SetLongField(env, jinfo, ProcessHandleImpl_Info_startTimeID, startTime);
    JNU_CHECK_EXCEPTION(env);

    // Get cputime if for current process
    if (pid == getpid()) {
        struct rusage usage;
        if (getrusage(RUSAGE_SELF, &usage) != 0) {
            return;
        }
        jlong microsecs =
            usage.ru_utime.tv_sec * 1000 * 1000 + usage.ru_utime.tv_usec +
            usage.ru_stime.tv_sec * 1000 * 1000 + usage.ru_stime.tv_usec;
        totalTime = microsecs * 1000;
        (*env)->SetLongField(env, jinfo, ProcessHandleImpl_Info_totalTimeID, totalTime);
        JNU_CHECK_EXCEPTION(env);
    }
}

/**
 * Construct the argument array by parsing the arguments from the sequence of arguments.
 */
static int fillArgArray(JNIEnv *env, jobject jinfo, int nargs,
                        const char *cp, const char *argsEnd) {
    jstring str = NULL;
    jobject argsArray;
    int i;

    if (nargs < 1) {
        return 0;
    }
    // Create a String array for nargs-1 elements
    CHECK_NULL_RETURN((argsArray = (*env)->NewObjectArray(env,
            nargs - 1, JNU_ClassString(env), NULL)), -1);

    for (i = 0; i < nargs - 1; i++) {
        // skip to the next argument; omits arg[0]
        cp += strnlen(cp, (argsEnd - cp)) + 1;

        if (cp > argsEnd || *cp == '\0') {
            return -2;  // Off the end pointer or an empty argument is an error
        }

        CHECK_NULL_RETURN((str = JNU_NewStringPlatform(env, cp)), -1);

        (*env)->SetObjectArrayElement(env, argsArray, i, str);
        JNU_CHECK_EXCEPTION_RETURN(env, -3);
    }
    (*env)->SetObjectField(env, jinfo, ProcessHandleImpl_Info_argumentsID, argsArray);
    JNU_CHECK_EXCEPTION_RETURN(env, -4);
    return 0;
}

/**
 * Retrieve the command and arguments for the process and store them
 * into the Info object.
 */
static void getCmdlineInfo(JNIEnv *env, jobject jinfo, pid_t pid) {
    int mib[3], maxargs, nargs, i;
    size_t size;
    char *args, *cp, *sp, *np;

    // Get the maximum size of the arguments
    mib[0] = CTL_KERN;
    mib[1] = KERN_ARGMAX;
    size = sizeof(maxargs);
    if (sysctl(mib, 2, &maxargs, &size, NULL, 0) == -1) {
            JNU_ThrowByNameWithLastError(env,
                    "java/lang/RuntimeException", "sysctl failed");
        return;
    }

    // Allocate an args buffer and get the arguments
    args = (char *)malloc(maxargs);
    if (args == NULL) {
        JNU_ThrowOutOfMemoryError(env, "malloc failed");
        return;
    }

    do {            // a block to break out of on error
        char *argsEnd;
        jstring str = NULL;

        mib[0] = CTL_KERN;
        mib[1] = KERN_PROCARGS2;
        mib[2] = pid;
        size = (size_t) maxargs;
        if (sysctl(mib, 3, args, &size, NULL, 0) == -1) {
            if (errno != EINVAL) {
            JNU_ThrowByNameWithLastError(env,
                    "java/lang/RuntimeException", "sysctl failed");
            }
            break;
        }
        memcpy(&nargs, args, sizeof(nargs));

        cp = &args[sizeof(nargs)];      // Strings start after nargs
        argsEnd = &args[size];

        // Store the command executable path
        if ((str = JNU_NewStringPlatform(env, cp)) == NULL) {
            break;
        }
        (*env)->SetObjectField(env, jinfo, ProcessHandleImpl_Info_commandID, str);
        if ((*env)->ExceptionCheck(env)) {
            break;
        }

        // Skip trailing nulls after the executable path
        for (cp = cp + strnlen(cp, argsEnd - cp); cp < argsEnd; cp++) {
            if (*cp != '\0') {
                break;
            }
        }

        fillArgArray(env, jinfo, nargs, cp, argsEnd);
    } while (0);
    // Free the arg buffer
    free(args);
}

