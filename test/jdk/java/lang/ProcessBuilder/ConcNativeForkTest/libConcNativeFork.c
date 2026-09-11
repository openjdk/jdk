/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, IBM Corp.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#include <assert.h>
#include <errno.h>
#include <pthread.h>
#include <sched.h>
#include <signal.h>
#include <stdbool.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <stdio.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>
#include <jni.h>
#include "testlib_thread_barriers.h"

static void trc(const char* fmt, ...) {
    char buf [1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    printf("(pid: %d): %s\n", (int)getpid(), buf);
    fflush(stdout);
}

static pthread_t tid_forker;

static pthread_barrier_t start_barrier;
static atomic_bool stop_now = false;

static void* forkerLoop(void* info) {

    const int numForks = (int)(intptr_t)info;
    pid_t* pids = calloc(numForks, sizeof(pid_t));

    trc("Forker: Waiting for Go.");

    pthread_barrier_wait(&start_barrier);

    for (int i = 0; i < numForks; i++) {
        const pid_t pid = fork();
        if (pid == 0) {
            /* Exec sleep. Properly opened file descriptors in parents (tagged CLOEXEC) should be released now.
             * Note that we use bash to not have to deal with path resolution. For our case, it does not matter if
             * sleep is a builtin or not. */
            char* env[] = { "PATH=/usr/bin:/bin", NULL };
            char* argv[] = { "sh", "-c", "sleep 30", NULL };
            execve("/bin/sh", argv, env);
            trc("Native child: sleep exec failed? %d", errno);
            /* The simplest way to handle this is to just wait here; this *will* cause the test to fail. */
            sleep(120);
            trc("Native child: exiting");
            exit(0);
        } else {
            pids[i] = pid;
            sched_yield();
        }
    }

    trc("Forker: All native child processes started.");

    /* Wait for test to signal end */
    while (!atomic_load(&stop_now)) {
        sleep(1);
    }

    trc("Forker: Cleaning up.");

    /* Reap children */
    for (int i = 0; i < numForks; i ++) {
        if (pids[i] != 0) {
            kill(pids[i], SIGKILL); /* if still running */
            waitpid(pids[i], NULL, 0);
        }
    }

    trc("Forker: Done.");

    return NULL;
}

JNIEXPORT jboolean JNICALL
Java_ConcNativeForkTest_prepareNativeForkerThread(JNIEnv* env, jclass cls, jint numForks)
{
    pthread_attr_t attr;
    int rc = 0;

    const int cap = 1000;
    const int numForksCapped = numForks > cap ? cap : numForks;
    if (numForks > numForksCapped) {
        trc("Main: Capping max. number of forks at %d", numForksCapped); /* don't forkbomb me */
    }

    if (pthread_barrier_init(&start_barrier, NULL, 2) != 0) {
        trc("Main: pthread_barrier_init failed (%d)", errno);
        return false;
    }

    pthread_attr_init(&attr);
    if (pthread_create(&tid_forker, &attr, forkerLoop, (void*)(intptr_t)numForksCapped) != 0) {
        trc("Main: pthread_create failed (%d)", errno);
        return JNI_FALSE;
    }

    trc("Main: Prepared native forker thread");

    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_ConcNativeForkTest_releaseNativeForkerThread(JNIEnv* env, jclass cls)
{
    pthread_barrier_wait(&start_barrier);
    trc("Main: signaled GO");
}

JNIEXPORT void JNICALL
Java_ConcNativeForkTest_stopNativeForkerThread(JNIEnv* env, jclass cls)
{
    atomic_store(&stop_now, true);
    pthread_join(tid_forker, NULL);
    pthread_barrier_destroy(&start_barrier);
}
