/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <stdlib.h>
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

#include <sys/poll.h>

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
 * The fd table and the number of file descriptors
 */
static fdEntry_t *fdTable;
static int fdCount;

/*
 * This limit applies if getlimit() returns unlimited.
 * Unfortunately, this means if someone wants a higher limit
 * then they have to set an explicit limit, higher than this,
 * which is probably counter-intuitive.
 */
#define MAX_FD_COUNT 4096

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
    int i;

    /*
     * Allocate table based on the maximum number of
     * file descriptors.
     */
    getrlimit(RLIMIT_NOFILE, &nbr_files);
    if (nbr_files.rlim_max == RLIM_INFINITY) {
        fdCount = MAX_FD_COUNT;
    } else {
        fdCount = nbr_files.rlim_max;
    }
    fdTable = (fdEntry_t *)calloc(fdCount, sizeof(fdEntry_t));
    if (fdTable == NULL) {
        fprintf(stderr, "library initialization failed - "
                "unable to allocate file descriptor table - out of memory");
        abort();
    }
    for (i=0; i<fdCount; i++) {
        pthread_mutex_init(&fdTable[i].lock, NULL);
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
 * Return the fd table for this fd or NULL is fd out
 * of range.
 */
static inline fdEntry_t *getFdEntry(int fd)
{
    if (fd < 0 || fd >= fdCount) {
        return NULL;
    }
    return &fdTable[fd];
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

/*
 * Close or dup2 a file descriptor ensuring that all threads blocked on
 * the file descriptor are notified via a wakeup signal.
 *
 *      fd1 < 0    => close(fd2)
 *      fd1 >= 0   => dup2(fd1, fd2)
 *
 * Returns -1 with errno set if operation fails.
 */
static int closefd(int fd1, int fd2) {
    int rv, orig_errno;
    fdEntry_t *fdEntry = getFdEntry(fd2);
    if (fdEntry == NULL) {
        errno = EBADF;
        return -1;
    }

    /*
     * Lock the fd to hold-off additional I/O on this fd.
     */
    pthread_mutex_lock(&(fdEntry->lock));

    {
        /*
         * Send a wakeup signal to all threads blocked on this
         * file descriptor.
         */
        threadEntry_t *curr = fdEntry->threads;
        while (curr != NULL) {
            curr->intr = 1;
            pthread_kill( curr->thr, sigWakeup );
            curr = curr->next;
        }

        /*
         * And close/dup the file descriptor
         * (restart if interrupted by signal)
         */
        do {
            if (fd1 < 0) {
                rv = close(fd2);
            } else {
                rv = dup2(fd1, fd2);
            }
        } while (rv == -1 && errno == EINTR);

    }

    /*
     * Unlock without destroying errno
     */
    orig_errno = errno;
    pthread_mutex_unlock(&(fdEntry->lock));
    errno = orig_errno;

    return rv;
}

/*
 * Wrapper for dup2 - same semantics as dup2 system call except
 * that any threads blocked in an I/O system call on fd2 will be
 * preempted and return -1/EBADF;
 */
int NET_Dup2(int fd, int fd2) {
    if (fd < 0) {
        errno = EBADF;
        return -1;
    }
    return closefd(fd, fd2);
}

/*
 * Wrapper for close - same semantics as close system call
 * except that any threads blocked in an I/O on fd will be
 * preempted and the I/O system call will return -1/EBADF.
 */
int NET_SocketClose(int fd) {
    return closefd(-1, fd);
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

int NET_Read(int s, void* buf, size_t len) {
    BLOCKING_IO_RETURN_INT( s, recv(s, buf, len, 0) );
}

int NET_ReadV(int s, const struct iovec * vector, int count) {
    BLOCKING_IO_RETURN_INT( s, readv(s, vector, count) );
}

int NET_RecvFrom(int s, void *buf, int len, unsigned int flags,
       struct sockaddr *from, int *fromlen) {
    /* casting int *fromlen -> socklen_t* Both are ints */
    BLOCKING_IO_RETURN_INT( s, recvfrom(s, buf, len, flags, from, (socklen_t *)fromlen) );
}

int NET_Send(int s, void *msg, int len, unsigned int flags) {
    BLOCKING_IO_RETURN_INT( s, send(s, msg, len, flags) );
}

int NET_WriteV(int s, const struct iovec * vector, int count) {
    BLOCKING_IO_RETURN_INT( s, writev(s, vector, count) );
}

int NET_SendTo(int s, const void *msg, int len,  unsigned  int
       flags, const struct sockaddr *to, int tolen) {
    BLOCKING_IO_RETURN_INT( s, sendto(s, msg, len, flags, to, tolen) );
}

int NET_Accept(int s, struct sockaddr *addr, int *addrlen) {
    socklen_t len = *addrlen;
    int error = accept(s, addr, &len);
    if (error != -1)
        *addrlen = (int)len;
    BLOCKING_IO_RETURN_INT( s, error );
}

int NET_Connect(int s, struct sockaddr *addr, int addrlen) {
    BLOCKING_IO_RETURN_INT( s, connect(s, addr, addrlen) );
}

#ifndef USE_SELECT
int NET_Poll(struct pollfd *ufds, unsigned int nfds, int timeout) {
    BLOCKING_IO_RETURN_INT( ufds[0].fd, poll(ufds, nfds, timeout) );
}
#else
int NET_Select(int s, fd_set *readfds, fd_set *writefds,
               fd_set *exceptfds, struct timeval *timeout) {
    BLOCKING_IO_RETURN_INT( s-1,
                            select(s, readfds, writefds, exceptfds, timeout) );
}
#endif

/*
 * Wrapper for select(s, timeout). We are using select() on Mac OS due to Bug 7131399.
 * Auto restarts with adjusted timeout if interrupted by
 * signal other than our wakeup signal.
 */
int NET_Timeout(int s, long timeout) {
    long prevtime = 0, newtime;
    struct timeval t, *tp = &t;
    fdEntry_t *fdEntry = getFdEntry(s);

    /*
     * Check that fd hasn't been closed.
     */
    if (fdEntry == NULL) {
        errno = EBADF;
        return -1;
    }

    /*
     * Pick up current time as may need to adjust timeout
     */
    if (timeout > 0) {
        /* Timed */
        struct timeval now;
        gettimeofday(&now, NULL);
        prevtime = now.tv_sec * 1000  +  now.tv_usec / 1000;
        t.tv_sec = timeout / 1000;
        t.tv_usec = (timeout % 1000) * 1000;
    } else if (timeout < 0) {
        /* Blocking */
        tp = 0;
    } else {
        /* Poll */
        t.tv_sec = 0;
        t.tv_usec = 0;
    }

    for(;;) {
        fd_set rfds;
        int rv;
        threadEntry_t self;

        /*
         * call select on the fd. If interrupted by our wakeup signal
         * errno will be set to EBADF.
         */
        FD_ZERO(&rfds);
        FD_SET(s, &rfds);

        startOp(fdEntry, &self);
        rv = select(s+1, &rfds, 0, 0, tp);
        endOp(fdEntry, &self);

        /*
         * If interrupted then adjust timeout. If timeout
         * has expired return 0 (indicating timeout expired).
         */
        if (rv < 0 && errno == EINTR) {
            if (timeout > 0) {
                struct timeval now;
                gettimeofday(&now, NULL);
                newtime = now.tv_sec * 1000  +  now.tv_usec / 1000;
                timeout -= newtime - prevtime;
                if (timeout <= 0) {
                    return 0;
                }
                prevtime = newtime;
                t.tv_sec = timeout / 1000;
                t.tv_usec = (timeout % 1000) * 1000;
            }
        } else {
            return rv;
        }

    }
}
