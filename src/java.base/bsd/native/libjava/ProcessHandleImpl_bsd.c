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

#include "ProcessHandleImpl_unix.h"

#include <stdio.h>
#include <errno.h>
#include <signal.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>

#include <sys/types.h>
#include <sys/resource.h>
#include <sys/sysctl.h>

#ifdef __FreeBSD__
#include <sys/param.h> // For MAXPATHLEN
#include <sys/user.h>  // For kinfo_proc
#endif

/* TODO: Refactor. */
#if defined(__OpenBSD__)
#define KERN_PROC_MIB  KERN_PROC
#define KINFO_PROC_T   kinfo_proc
#define KI_PID         p_pid
#define KI_PPID        p_ppid
#define KI_UID         p_uid
#define KI_START_SEC   p_ustart_sec
#define KI_START_USEC  p_ustart_usec
#elif defined(__FreeBSD__)
#define KINFO_PROC_T   kinfo_proc
#define KI_PID         ki_pid
#define KI_PPID        ki_ppid
#define KI_UID         ki_uid
#define KI_START_SEC   ki_start.tv_sec
#define KI_START_USEC  ki_start.tv_usec
#elif defined(__NetBSD__)
#define KERN_PROC_MIB  KERN_PROC2
#define KINFO_PROC_T   kinfo_proc2
#define KI_PID         p_pid
#define KI_PPID        p_ppid
#define KI_UID         p_uid
#define KI_START_SEC   p_ustart_sec
#define KI_START_USEC  p_ustart_usec
#endif

/**
 * Implementation of native ProcessHandleImpl functions for BSD's.
 * See ProcessHandleImpl_unix.c for more details.
 */

void os_initNative(JNIEnv *env, jclass clazz) {}

/*
 * Return pids of active processes, and optionally parent pids and
 * start times for each process.
 * For a specific non-zero pid jpid, only the direct children are returned.
 * If the pid jpid is zero, all active processes are returned.
 * Uses sysctl to accumulates any process following the rules above.
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
    jlong* pids = NULL;
    jlong* ppids = NULL;
    jlong* stimes = NULL;
    jsize parentArraySize = 0;
    jsize arraySize = 0;
    jsize stimesSize = 0;
    jsize count = 0;
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
    if (jstimesArray != NULL) {
        stimesSize = (*env)->GetArrayLength(env, jstimesArray);
        JNU_CHECK_EXCEPTION_RETURN(env, -1);

        if (arraySize != stimesSize) {
            JNU_ThrowIllegalArgumentException(env, "array sizes not equal");
            return 0;
        }
    }

    // Get buffer size needed to read all processes
#ifndef __FreeBSD__
    u_int namelen = 6;
    int mib[6] = {CTL_KERN, KERN_PROC_MIB, KERN_PROC_ALL, 0, sizeof(struct KINFO_PROC_T), 0};
#else
    u_int namelen = 3;
    int mib[3] = {CTL_KERN, KERN_PROC, KERN_PROC_PROC};
#endif
    if (sysctl(mib, namelen, NULL, &bufSize, NULL, 0) < 0) {
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

#ifndef __FreeBSD__
    mib[5] = bufSize / sizeof(struct KINFO_PROC_T);
#endif

    // Read process info for all processes
    if (sysctl(mib, namelen, buffer, &bufSize, NULL, 0) < 0) {
        JNU_ThrowByNameWithLastError(env,
            "java/lang/RuntimeException", "sysctl failed");
        free(buffer);
        return -1;
    }

    do { // Block to break out of on Exception
        struct KINFO_PROC_T *kp = (struct KINFO_PROC_T *) buffer;
        unsigned long nentries = bufSize / sizeof (struct KINFO_PROC_T);
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
        if (jstimesArray != NULL) {
            stimes  = (*env)->GetLongArrayElements(env, jstimesArray, NULL);
            if (stimes == NULL) {
                break;
            }
        }

        // Process each entry in the buffer
        for (i = nentries; --i >= 0; ++kp) {
            if (pid == 0 || kp->KI_PPID == pid) {
                if (count < arraySize) {
                    // Only store if it fits
                    pids[count] = (jlong) kp->KI_PID;
                    if (ppids != NULL) {
                        // Store the parentPid
                        ppids[count] = (jlong) kp->KI_PPID;
                    }
                    if (stimes != NULL) {
                        // Store the process start time
                        jlong startTime = kp->KI_START_SEC * 1000 +
                                          kp->KI_START_USEC / 1000;
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

    free(buffer);
    // If more pids than array had size for; count will be greater than array size
    return count;
}

/**
 * Use sysctl and return the ppid, total cputime and start time.
 * Return: -1 is fail;  >=  0 is parent pid
 * 'total' will contain the running time of 'pid' in nanoseconds.
 * 'start' will contain the start time of 'pid' in milliseconds since epoch.
 */
pid_t os_getParentPidAndTimings(JNIEnv *env, pid_t jpid,
                                jlong *totalTime, jlong *startTime) {
    const pid_t pid = (pid_t) jpid;
    pid_t ppid = -1;
    struct KINFO_PROC_T kp;
    size_t bufSize = sizeof kp;

    // Read the process info for the specific pid
#ifndef __FreeBSD__
    u_int namelen = 6;
    int mib[6] = {CTL_KERN, KERN_PROC_MIB, KERN_PROC_PID, pid, bufSize, 1};
#else
    u_int namelen = 4;
    int mib[4] = {CTL_KERN, KERN_PROC, KERN_PROC_PID, pid};
#endif

    if (sysctl(mib, namelen, &kp, &bufSize, NULL, 0) == -1) {
        /*
         * Check errno and throw an exception only if appropriate.
         *
         * ESRCH - No such process
         *         This can happen if the process completes before the JVM
         *         attempts to check if it is alive.  If so, no exception
         *         should be thrown as this is normal behaviour.
         *
         * EPERM - Operation not permitted
         *         This may happen if the user doesn't have permission to
         *         check the given pid.  No exception should be thrown in
         *         this case.
         */
        if (errno != ESRCH && errno != EPERM) {
            JNU_ThrowByNameWithLastError(env,
                "java/lang/RuntimeException", "sysctl failed");
        }
        return -1;
    }
    if (bufSize > 0 && kp.KI_PID == pid) {
        *startTime = (jlong) (kp.KI_START_SEC * 1000 +
                              kp.KI_START_USEC / 1000);
        ppid = kp.KI_PPID;
    }

#ifndef __FreeBSD__
    jlong microsecs = kp.p_uutime_sec * 1000 * 1000 + kp.p_uutime_usec +
        kp.p_ustime_sec * 1000 * 1000 + kp.p_ustime_usec;
    *totalTime = microsecs * 1000;
#else
    // Get cputime if for current process
    if (pid == getpid()) {
        struct rusage usage;
        if (getrusage(RUSAGE_SELF, &usage) == 0) {
          jlong microsecs =
              usage.ru_utime.tv_sec * 1000 * 1000 + usage.ru_utime.tv_usec +
              usage.ru_stime.tv_sec * 1000 * 1000 + usage.ru_stime.tv_usec;
          *totalTime = microsecs * 1000;
        }
    }
#endif

    return ppid;
}

/**
 * Return the uid of a process or -1 on error
 */
static uid_t getUID(pid_t pid) {
    struct KINFO_PROC_T kp;
    size_t bufSize = sizeof kp;

    // Read the process info for the specific pid
#ifndef __FreeBSD__
    u_int namelen = 6;
    int mib[6] = {CTL_KERN, KERN_PROC_MIB, KERN_PROC_PID, pid, bufSize, 1};
#else
    u_int namelen = 4;
    int mib[4] = {CTL_KERN, KERN_PROC, KERN_PROC_PID, pid};
#endif

    if (sysctl(mib, namelen, &kp, &bufSize, NULL, 0) == 0) {
        if (bufSize > 0 && kp.KI_PID == pid) {
            return kp.KI_UID;
        }
    }
    return (uid_t)-1;
}

/**
 * Retrieve the command and arguments for the process and store them
 * into the Info object.
 */
void os_getCmdlineAndUserInfo(JNIEnv *env, jobject jinfo, pid_t pid) {
    int mib[4], nargs, i;
    size_t size;
    char *args;

    // Get the UID first. This is done here because it is cheap to do it here
    // on other platforms like Linux/Solaris/AIX where the uid comes from the
    // same source like the command line info.
    unix_getUserInfo(env, jinfo, getUID(pid));

#ifdef __OpenBSD__
    // Get the buffer size needed
    mib[0] = CTL_KERN;
    mib[1] = KERN_PROC_ARGS;
    mib[2] = pid;
    mib[3] = KERN_PROC_ARGV;

    if (sysctl(mib, 4, NULL, &size, NULL, 0) == -1) {
        JNU_ThrowByNameWithLastError(env,
            "java/lang/RuntimeException", "sysctl failed");
        return;
    }

    // Allocate space for args and get the arguments
    args = (char *)malloc(size);
    if (args == NULL) {
        JNU_ThrowOutOfMemoryError(env, "malloc failed");
        return;
    }

    do {            // a block to break out of on error
        char **argv;
        jstring cmdexe = NULL;
        jclass clazzString;
        jobject argsArray;

        if (sysctl(mib, 4, args, &size, NULL, 0) == -1) {
            if (errno != EINVAL) {
                JNU_ThrowByNameWithLastError(env,
                    "java/lang/RuntimeException", "sysctl failed");
            }
            break;
        }

        // count the number of argv elements
        argv = (char **)args;
        nargs = 0;
        while (*argv++)
            nargs++;

        if (nargs < 1)
            break;

        // reset argv and store command executable path
        argv = (char **)args;
        if ((cmdexe = JNU_NewStringPlatform(env, *argv++)) == NULL)
            break;
        (*env)->SetObjectField(env, jinfo, ProcessHandleImpl_Info_commandID, cmdexe);
        if ((*env)->ExceptionCheck(env))
            break;
        nargs--;

        // process remaining arguments
        // Create a String array for nargs elements
        if ((clazzString = JNU_ClassString(env)) == NULL)
            break;
        if ((argsArray = (*env)->NewObjectArray(env, nargs, clazzString, NULL)) == NULL)
            break;

        for (i = 0; i < nargs; i++) {
            jstring str;
            if ((str = JNU_NewStringPlatform(env, argv[i])) == NULL)
                break;

            (*env)->SetObjectArrayElement(env, argsArray, i, str);
            if ((*env)->ExceptionCheck(env))
                break;
        }
        if (i == nargs) // no errors in for loop?
           (*env)->SetObjectField(env, jinfo, ProcessHandleImpl_Info_argumentsID, argsArray);
    } while (0);
    // Free the arg buffer
    free(args);
#else
    int maxargs;
    char cmd[MAXPATHLEN];
    jstring cmdexe = NULL;

    // Get the resolved name of the executable
    size = sizeof(cmd);
    mib[0] = CTL_KERN;
    mib[1] = KERN_PROC;
    mib[2] = KERN_PROC_PATHNAME;
    mib[3] = pid;
    if (sysctl(mib, 4, cmd, &size, NULL, 0) == -1) {
        if (errno != EINVAL && errno != ESRCH && errno != EPERM && errno != ENOENT) {
            JNU_ThrowByNameWithLastError(env,
                "java/lang/RuntimeException", "sysctl failed");
        }
        return;
    }
    // Make sure it is null terminated
    cmd[MAXPATHLEN - 1] = '\0';

    // Store the command executable
    if ((cmdexe = JNU_NewStringPlatform(env, cmd)) == NULL) {
        return;
    }

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

    // A block to break out of on error
    do {
        char *cp, *argsEnd = NULL;

        mib[0] = CTL_KERN;
        mib[1] = KERN_PROC;
        mib[2] = KERN_PROC_ARGS;
        mib[3] = pid;
        size = (size_t) maxargs;
        if (sysctl(mib, 4, args, &size, NULL, 0) == -1) {
            if (errno != EINVAL && errno != ESRCH && errno != EPERM && errno != ENOENT) {
                JNU_ThrowByNameWithLastError(env,
                    "java/lang/RuntimeException", "sysctl failed");
            }
            break;
        }

        // At this point args should hold a flattened argument string with
        // arguments delimited by NUL and size should hold the overall length
        // of the string

        // Make sure the string is NUL terminated
        args[size] = '\0';

        // Count the number of arguments
        nargs = 0;
        argsEnd = &args[size];
        for (cp = args; *cp != '\0' && (cp < argsEnd); nargs++) {
            cp += strnlen(cp, (argsEnd - cp)) + 1;
        }

        // Copy over all the args
        cp = args;
        unix_fillArgArray(env, jinfo, nargs, cp, argsEnd, cmdexe, args);
    } while (0);

    // Free the arg buffer
    free(args);
#endif
}
