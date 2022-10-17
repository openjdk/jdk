/*
 * Copyright (c) 2001, 2022, Oracle and/or its affiliates. All rights reserved.
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

#include <assert.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/param.h>
#include <signal.h>
#include <pthread.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/select.h>
#include <sys/time.h>
#include <sys/resource.h>
#include <sys/uio.h>
#include <unistd.h>
#include <errno.h>
#include <poll.h>
#include "jvm.h"
#include "net_util.h"

/*
 * Stack allocated by thread when doing blocking operation
 */
typedef struct threadEntry {
    pthread_t thr;                      /* this thread */
    struct threadEntry *next;           /* next thread */
    int intr;                           /* interrupted */
} threadEntry_t;

/*
 * Heap allocated during initialized - one entry per fd
 */
typedef struct {
    pthread_mutex_t lock;               /* fd lock */
    threadEntry_t *threads;             /* threads blocked on fd */
} fdEntry_t;

/*
 * Signal to unblock thread
 */
static int sigWakeup = SIGIO;

/*
 * fdTable holds one entry per file descriptor, up to a certain
 * maximum.
 * Theoretically, the number of possible file descriptors can get
 * large, though usually it does not. Entries for small value file
 * descriptors are kept in a simple table, which covers most scenarios.
 * Entries for large value file descriptors are kept in an overflow
 * table, which is organized as a sparse two dimensional array whose
 * slabs are allocated on demand. This covers all corner cases while
 * keeping memory consumption reasonable.
 */

/* Base table for low value file descriptors */
static fdEntry_t* fdTable = NULL;
/* Maximum size of base table (in number of entries). */
static const int fdTableMaxSize = 0x1000; /* 4K */
/* Actual size of base table (in number of entries) */
static int fdTableLen = 0;
/* Max. theoretical number of file descriptors on system. */
static int fdLimit = 0;

/* Overflow table, should base table not be large enough. Organized as
 *   an array of n slabs, each holding 64k entries.
 */
static fdEntry_t** fdOverflowTable = NULL;
/* Number of slabs in the overflow table */
static int fdOverflowTableLen = 0;
/* Number of entries in one slab */
static const int fdOverflowTableSlabSize = 0x10000; /* 64k */
pthread_mutex_t fdOverflowTableLock = PTHREAD_MUTEX_INITIALIZER;

/*
 * Null signal handler
 */
static void sig_wakeup(int sig) {
}

/*
 * Initialization routine (executed when library is loaded)
 * Allocate fd tables and sets up signal handler.
 */
static void __attribute((constructor)) init() {
    struct rlimit nbr_files;
    sigset_t sigset;
    struct sigaction sa;
    int i = 0;

    /* Determine the maximum number of possible file descriptors. */
    if (-1 == getrlimit(RLIMIT_NOFILE, &nbr_files)) {
        fprintf(stderr, "library initialization failed - "
                "unable to get max # of allocated fds\n");
        abort();
    }
    if (nbr_files.rlim_max != RLIM_INFINITY) {
        fdLimit = nbr_files.rlim_max;
    } else {
        /* We just do not know. */
        fdLimit = INT_MAX;
    }

    /* Allocate table for low value file descriptors. */
    fdTableLen = fdLimit < fdTableMaxSize ? fdLimit : fdTableMaxSize;
    fdTable = (fdEntry_t*) calloc(fdTableLen, sizeof(fdEntry_t));
    if (fdTable == NULL) {
        fprintf(stderr, "library initialization failed - "
                "unable to allocate file descriptor table - out of memory");
        abort();
    } else {
        for (i = 0; i < fdTableLen; i ++) {
            pthread_mutex_init(&fdTable[i].lock, NULL);
        }
    }

    /* Allocate overflow table, if needed */
    if (fdLimit > fdTableMaxSize) {
        fdOverflowTableLen = ((fdLimit - fdTableMaxSize) / fdOverflowTableSlabSize) + 1;
        fdOverflowTable = (fdEntry_t**) calloc(fdOverflowTableLen, sizeof(fdEntry_t*));
        if (fdOverflowTable == NULL) {
            fprintf(stderr, "library initialization failed - "
                    "unable to allocate file descriptor overflow table - out of memory");
            abort();
        }
    }

    /*
     * Setup the signal handler
     */
    sa.sa_handler = sig_wakeup;
    sa.sa_flags   = 0;
    sigemptyset(&sa.sa_mask);
    sigaction(sigWakeup, &sa, NULL);

    sigemptyset(&sigset);
    sigaddset(&sigset, sigWakeup);
    sigprocmask(SIG_UNBLOCK, &sigset, NULL);
}

/*
 * Return the fd table for this fd.
 */
static inline fdEntry_t *getFdEntry(int fd)
{
    fdEntry_t* result = NULL;

    if (fd < 0) {
        return NULL;
    }

    /* This should not happen. If it does, our assumption about
     * max. fd value was wrong. */
    assert(fd < fdLimit);

    if (fd < fdTableMaxSize) {
        /* fd is in base table. */
        assert(fd < fdTableLen);
        result = &fdTable[fd];
    } else {
        /* fd is in overflow table. */
        const int indexInOverflowTable = fd - fdTableMaxSize;
        const int rootindex = indexInOverflowTable / fdOverflowTableSlabSize;
        const int slabindex = indexInOverflowTable % fdOverflowTableSlabSize;
        fdEntry_t* slab = NULL;
        assert(rootindex < fdOverflowTableLen);
        assert(slabindex < fdOverflowTableSlabSize);
        pthread_mutex_lock(&fdOverflowTableLock);
        /* Allocate new slab in overflow table if needed */
        if (fdOverflowTable[rootindex] == NULL) {
            fdEntry_t* const newSlab =
                (fdEntry_t*)calloc(fdOverflowTableSlabSize, sizeof(fdEntry_t));
            if (newSlab == NULL) {
                fprintf(stderr, "Unable to allocate file descriptor overflow"
                        " table slab - out of memory");
                pthread_mutex_unlock(&fdOverflowTableLock);
                abort();
            } else {
                int i;
                for (i = 0; i < fdOverflowTableSlabSize; i ++) {
                    pthread_mutex_init(&newSlab[i].lock, NULL);
                }
                fdOverflowTable[rootindex] = newSlab;
            }
        }
        pthread_mutex_unlock(&fdOverflowTableLock);
        slab = fdOverflowTable[rootindex];
        result = &slab[slabindex];
    }

    return result;

}


/*
 * Start a blocking operation :-
 *    Insert thread onto thread list for the fd.
 */
static inline void startOp(fdEntry_t *fdEntry, threadEntry_t *self)
{
    self->thr = pthread_self();
    self->intr = 0;

    pthread_mutex_lock(&(fdEntry->lock));
    {
        self->next = fdEntry->threads;
        fdEntry->threads = self;
    }
    pthread_mutex_unlock(&(fdEntry->lock));
}

/*
 * End a blocking operation :-
 *     Remove thread from thread list for the fd
 *     If fd has been interrupted then set errno to EBADF
 */
static inline void endOp
    (fdEntry_t *fdEntry, threadEntry_t *self)
{
    int orig_errno = errno;
    pthread_mutex_lock(&(fdEntry->lock));
    {
        threadEntry_t *curr, *prev=NULL;
        curr = fdEntry->threads;
        while (curr != NULL) {
            if (curr == self) {
                if (curr->intr) {
                    orig_errno = EBADF;
                }
                if (prev == NULL) {
                    fdEntry->threads = curr->next;
                } else {
                    prev->next = curr->next;
                }
                break;
            }
            prev = curr;
            curr = curr->next;
        }
    }
    pthread_mutex_unlock(&(fdEntry->lock));
    errno = orig_errno;
}

/************** Basic I/O operations here ***************/

/*
 * Macro to perform a blocking IO operation. Restarts
 * automatically if interrupted by signal (other than
 * our wakeup signal)
 */
#define BLOCKING_IO_RETURN_INT(FD, FUNC) {      \
    int ret;                                    \
    threadEntry_t self;                         \
    fdEntry_t *fdEntry = getFdEntry(FD);        \
    if (fdEntry == NULL) {                      \
        errno = EBADF;                          \
        return -1;                              \
    }                                           \
    do {                                        \
        startOp(fdEntry, &self);                \
        ret = FUNC;                             \
        endOp(fdEntry, &self);                  \
    } while (ret == -1 && errno == EINTR);      \
    return ret;                                 \
}

int NET_Connect(int s, struct sockaddr *addr, int addrlen) {
    BLOCKING_IO_RETURN_INT( s, connect(s, addr, addrlen) );
}

int NET_Poll(struct pollfd *ufds, unsigned int nfds, int timeout) {
    BLOCKING_IO_RETURN_INT( ufds[0].fd, poll(ufds, nfds, timeout) );
}
