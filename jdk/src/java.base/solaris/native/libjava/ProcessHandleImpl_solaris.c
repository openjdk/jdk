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
#include <ctype.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <procfs.h>
#include <signal.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <limits.h>

/**
 * Implementations of ProcessHandleImpl functions that are
 * NOT common to all Unix variants:
 * - getProcessPids0(pid, pidArray)
 *
 * Implementations of ProcessHandleImpl_Info
 * - totalTime, startTime
 * - Command
 * - Arguments
 */

/*
 * Signatures for internal OS specific functions.
 */
static pid_t getStatInfo(JNIEnv *env, pid_t pid,
                                     jlong *totalTime, jlong* startTime,
                                     uid_t *uid);
static void getCmdlineInfo(JNIEnv *env, jobject jinfo, pid_t pid);

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
JNIEXPORT void JNICALL
Java_java_lang_ProcessHandleImpl_00024Info_initIDs(JNIEnv *env, jclass clazz) {
    CHECK_NULL(ProcessHandleImpl_Info_commandID = (*env)->GetFieldID(env,
        clazz, "command", "Ljava/lang/String;"));
    CHECK_NULL(ProcessHandleImpl_Info_argumentsID = (*env)->GetFieldID(env,
        clazz, "arguments", "[Ljava/lang/String;"));
    CHECK_NULL(ProcessHandleImpl_Info_totalTimeID = (*env)->GetFieldID(env,
        clazz, "totalTime", "J"));
    CHECK_NULL(ProcessHandleImpl_Info_startTimeID = (*env)->GetFieldID(env,
        clazz, "startTime", "J"));
    CHECK_NULL(ProcessHandleImpl_Info_userID = (*env)->GetFieldID(env,
        clazz, "user", "Ljava/lang/String;"));
}

/**************************************************************
 * Static method to initialize the ticks per second rate.
 *
 * Class:     java_lang_ProcessHandleImpl
 * Method:    initNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_lang_ProcessHandleImpl_initNative(JNIEnv *env, jclass clazz) {
    clock_ticks_per_second = sysconf(_SC_CLK_TCK);
}

/*
 * Check if a process is alive.
 * Return the start time (ms since 1970) if it is available.
 * If the start time is not available return 0.
 * If the pid is invalid, return -1.
 *
 * Class:     java_lang_ProcessHandleImpl
 * Method:    isAlive0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_java_lang_ProcessHandleImpl_isAlive0(JNIEnv *env, jobject obj, jlong jpid) {
    pid_t pid = (pid_t) jpid;
    jlong startTime = 0L;
    jlong totalTime = 0L;
    uid_t uid = -1;
    pid_t ppid = getStatInfo(env, pid, &totalTime, &startTime, &uid);
    return (ppid < 0) ? -1 : startTime;
}

/*
 * Returns the parent pid of the requested pid.
 *
 * Class:     java_lang_ProcessHandleImpl
 * Method:    parent0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_java_lang_ProcessHandleImpl_parent0(JNIEnv *env,
                                         jobject obj,
                                         jlong jpid,
                                         jlong startTime) {
    pid_t pid = (pid_t) jpid;
    pid_t ppid = -1;

    if (pid == getpid()) {
        ppid = getppid();
    } else {
        jlong start = 0L;
        jlong total = 0L;
        uid_t uid = -1;

        pid_t ppid = getStatInfo(env, pid, &total, &start, &uid);
        if (start != startTime
            && start != 0
            && startTime != 0) {
            ppid = -1;
        }
    }
    return (jlong) ppid;
}

/*
 * Returns the children of the requested pid and optionally each parent.
 *
 * Class:     java_lang_ProcessHandleImpl
 * Method:    getChildPids
 * Signature: (J[J)I
 *
 * Reads /proc and accumulates any process who parent pid matches.
 * The resulting pids are stored into the array of longs.
 * The number of pids is returned if they all fit.
 * If the array is too short, the desired length is returned.
 */
JNIEXPORT jint JNICALL
Java_java_lang_ProcessHandleImpl_getProcessPids0(JNIEnv *env,
                                                 jclass clazz,
                                                 jlong jpid,
                                                 jlongArray jarray,
                                                 jlongArray jparentArray,
                                                 jlongArray jstimesArray) {
    DIR* dir;
    struct dirent* ptr;
    pid_t pid = (pid_t) jpid;
    jlong* pids = NULL;
    jlong* ppids = NULL;
    jlong* stimes = NULL;
    jsize parentArraySize = 0;
    jsize arraySize = 0;
    jsize stimesSize = 0;
    jsize count = 0;
    char procname[32];

    arraySize = (*env)->GetArrayLength(env, jarray);
    JNU_CHECK_EXCEPTION_RETURN(env, 0);
    if (jparentArray != NULL) {
        parentArraySize = (*env)->GetArrayLength(env, jparentArray);
        JNU_CHECK_EXCEPTION_RETURN(env, 0);

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

    /*
     * To locate the children we scan /proc looking for files that have a
     * positive integer as a filename.
     */
    if ((dir = opendir("/proc")) == NULL) {
        JNU_ThrowByNameWithLastError(env,
            "java/lang/Runtime", "Unable to open /proc");
        return 0;
    }

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

        while ((ptr = readdir(dir)) != NULL) {
            pid_t ppid = 0;
            jlong totalTime = 0L;
            jlong startTime = 0L;
            uid_t uid; // value unused

            /* skip files that aren't numbers */
            pid_t childpid = (pid_t) atoi(ptr->d_name);
            if ((int) childpid <= 0) {
                continue;
            }

            // Read /proc/pid/stat and get the parent pid, and start time
            ppid = getStatInfo(env, childpid, &totalTime, &startTime, &uid);
            if (ppid >= 0 && (pid == 0 || ppid == pid)) {
                if (count < arraySize) {
                    // Only store if it fits
                    pids[count] = (jlong) childpid;

                    if (ppids != NULL) {
                        // Store the parent Pid
                        ppids[count] = (jlong) ppid;
                    }
                    if (stimes != NULL) {
                        // Store the process start time
                        stimes[count] = startTime;
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
    if (stimes != NULL) {
        (*env)->ReleaseLongArrayElements(env, jstimesArray, stimes, 0);
    }

    closedir(dir);
    // If more pids than array had size for; count will be greater than array size
    return count;
}

/**************************************************************
 * Implementation of ProcessHandleImpl_Info native methods.
 */

/*
 * Fill in the Info object from the OS information about the process.
 *
 * Class:     java_lang_ProcessHandleImpl_Info
 * Method:    info0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_java_lang_ProcessHandleImpl_00024Info_info0(JNIEnv *env,
                                                 jobject jinfo,
                                                  jlong jpid) {
    pid_t pid = (pid_t) jpid;
    jlong startTime = 0L;
    jlong totalTime = 0L;
    uid_t uid = -1;
    pid_t ppid = getStatInfo(env, pid, &totalTime, &startTime, &uid);

    getCmdlineInfo(env, jinfo, pid);

    if (ppid > 0) {
        jstring str;
        (*env)->SetLongField(env, jinfo, ProcessHandleImpl_Info_startTimeID, startTime);
        JNU_CHECK_EXCEPTION(env);

        (*env)->SetLongField(env, jinfo, ProcessHandleImpl_Info_totalTimeID, totalTime);
        JNU_CHECK_EXCEPTION(env);

        CHECK_NULL((str = uidToUser(env, uid)));
        (*env)->SetObjectField(env, jinfo, ProcessHandleImpl_Info_userID, str);
        JNU_CHECK_EXCEPTION(env);
    }
}

/**
 * Read /proc/<pid>/status and return the ppid, total cputime and start time.
 * Return: -1 is fail;  zero is unknown; >  0 is parent pid
 */
static pid_t getStatInfo(JNIEnv *env, pid_t pid,
                                      jlong *totalTime, jlong* startTime,
                                      uid_t* uid) {
    FILE* fp;
    psinfo_t psinfo;
    char fn[32];
    int ret;

    /*
     * Try to open /proc/%d/status
     */
    snprintf(fn, sizeof fn, "/proc/%d/psinfo", pid);
    fp = fopen(fn, "r");
    if (fp == NULL) {
        return -1;
    }

    ret = fread(&psinfo, 1, (sizeof psinfo), fp);
    fclose(fp);
    if (ret < (sizeof psinfo)) {
        return -1;
    }

    *totalTime = psinfo.pr_time.tv_sec * 1000000000L + psinfo.pr_time.tv_nsec;

    *startTime = psinfo.pr_start.tv_sec * (jlong)1000 +
                 psinfo.pr_start.tv_nsec / 1000000;

    *uid = psinfo.pr_uid;

    return (pid_t) psinfo.pr_ppid;
}

static void getCmdlineInfo(JNIEnv *env, jobject jinfo, pid_t pid) {
    char fn[32];
    char exePath[PATH_MAX];
    jstring str = NULL;
    int ret;

    /*
     * The path to the executable command is the link in /proc/<pid>/paths/a.out.
     */
    snprintf(fn, sizeof fn, "/proc/%d/path/a.out", pid);
    if ((ret = readlink(fn, exePath, PATH_MAX - 1)) < 0) {
        return;
    }

    // null terminate and create String to store for command
    exePath[ret] = '\0';
    CHECK_NULL(str = JNU_NewStringPlatform(env, exePath));
    (*env)->SetObjectField(env, jinfo, ProcessHandleImpl_Info_commandID, str);
    JNU_CHECK_EXCEPTION(env);
}

