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
#include <fcntl.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <jni.h>
#include "testlib_thread_barriers.h"

static void trc(const char* fmt, ...) {
    char buf [1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    printf("%s\n", buf);
    fflush(stdout);
}

/* Set 1 to restrict this test to pipes, 0 to test all file descriptors.
 * (for now, we ignore regular files opened with CLOEXEC since loaded jars seem not tagged as CLOEXEc.
 * We should probably fix that eventually. */
#define TEST_PIPES_ONLY 1

/* stdin/out/err file descriptors are usually not CLOEXEC */
#define IGNORE_BELOW 4

/* Only query file descriptors up to this point */
#define MAX_FD 1024

static pthread_t tid_tester;

static pthread_barrier_t start_barrier;
static atomic_bool stop_now = false;

/* Mainly to prevent tracing the same fd over and over again:
 *   1 - present, 2 - present, cloexec */
static unsigned fd_state[MAX_FD];

static bool is_pipe(int fd) {
    struct stat mystat;
    if (fstat(fd, &mystat) == -1) {
        return false;
    }
    return mystat.st_mode & S_IFIFO;
}

static void print_fd_details(int fd, char* out, size_t outlen) {
    const char* type = "unknown";
    char link[1024] = { 0 };
    char procfd[129];
    struct stat mystat;

    out[0] = '\0';

    if (fstat(fd, &mystat) == -1) {
        snprintf(out, outlen, "%s", errno == EBADF ? "EBADF" : "???");
        return;
    }

    switch (mystat.st_mode & S_IFMT) {
        case S_IFBLK:  type = "blk"; break;
        case S_IFCHR:  type = "char"; break;
        case S_IFDIR:  type = "dir"; break;
        case S_IFIFO:  type = "fifo"; break;
        case S_IFLNK:  type = "lnk"; break;
        case S_IFREG:  type = "reg"; break;
        case S_IFSOCK: type = "sock"; break;
    }

    snprintf(procfd, sizeof(procfd) - 1, "/proc/self/fd/%d", fd);
    int linklen = readlink(procfd, link, sizeof(link) - 1);
    if (linklen > 0) {
        link[linklen] = '\0';
        snprintf(out, outlen, "%s (%s)", type, link);
    } else {
        snprintf(out, outlen, "%s", type);
    }
}

/* Returns true for error */
static bool testFD(int fd) {

    int rc = fcntl(fd, F_GETFD);
    if (rc == -1) {
        return false;
    }

    const bool has_cloexec = (rc & FD_CLOEXEC);
    const bool is_a_pipe = is_pipe(fd);
    const unsigned state = has_cloexec ? 2 : 1;
    bool had_error = false;

    if (fd_state[fd] != state) {
        fd_state[fd] = state;
        char buf[1024];
        print_fd_details(fd, buf, sizeof(buf));
        if (has_cloexec) {
            trc("%d: %s", fd, buf);
        } else {
            if (fd < IGNORE_BELOW) {
                trc("%d: %s ** CLOEXEC MISSING ** (ignored - below scanned range)", fd, buf);
            } else if (TEST_PIPES_ONLY && !is_a_pipe) {
                trc("%d: %s ** CLOEXEC MISSING ** (ignored - not a pipe)", fd, buf);
            } else {
                trc("%d: %s ** CLOEXEC MISSING ** (ERROR)", fd, buf);
                had_error = true;
            }
        }
    }

    return had_error;
}

static void* testerLoop(void* dummy) {

    pthread_barrier_wait(&start_barrier);

    trc("Tester is alive");

    bool had_error = false;

    while (!atomic_load(&stop_now)) {
        for (int fd = 0; fd < MAX_FD; fd++) {
            bool rc = testFD(fd);
            had_error = had_error || rc;
        }
    }

    trc("Tester dies");

    return had_error ? (void*)1 : NULL;
}

JNIEXPORT jboolean JNICALL
Java_PipesCloseOnExecTest_startTester(JNIEnv* env, jclass cls)
{
    pthread_attr_t attr;
    int rc = 0;

    if (pthread_barrier_init(&start_barrier, NULL, 2) != 0) {
        trc("pthread_barrier_init failed (%d)", errno);
        return false;
    }

    pthread_attr_init(&attr);
    if (pthread_create(&tid_tester, &attr, testerLoop, NULL) != 0) {
        trc("pthread_create failed (%d)", errno);
        return JNI_FALSE;
    }

    pthread_barrier_wait(&start_barrier);

    trc("Started tester");

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_PipesCloseOnExecTest_stopTester(JNIEnv* env, jclass cls)
{
    atomic_store(&stop_now, true);

    void* retval = NULL;
    pthread_join(tid_tester, &retval);
    pthread_barrier_destroy(&start_barrier);

    return retval == NULL ? JNI_TRUE : JNI_FALSE;
}
