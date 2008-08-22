/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * Solaris-dependent I/O Note: Routines here are just place holders -
 * eventually we need to put in a solution that involves using
 * setjmp/longjmp to avoid io races.  Look at green_threads/io_md.c for
 * more detailed comments on the architechture of the io modules.
 */
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>

#ifdef HAVE_FILIOH
#include <sys/filio.h>
#else
#include <sys/ioctl.h>
#endif

#include <sys/socket.h>
#include <setjmp.h>
#include <signal.h>
#ifndef USE_SELECT
#include <poll.h>
#endif

#include "hpi_impl.h"

#include "threads_md.h"
#include "io_md.h"
#include "largefile.h"
#include "mutex_md.h"

#if defined(__solaris__) && defined(NO_INTERRUPTIBLE_IO)
#error If there was no policy change, this could be a makefile error.
#endif

#ifdef NO_INTERRUPTIBLE_IO
#undef CLOSEIO
#else
#define CLOSEIO
#endif /* NO_INTERRUPTIBLE_IO */

/* Get typedef for rlim_t */
#include <sys/resource.h>

#ifdef CLOSEIO

/*
 * Structure for file control block, used by closable IO.
 * We should NOT add more field into the data structure.
 * Otherwise, the sysRead() will only work with sysOpen,
 * and may NOT work with a fd return by open()
 */
typedef struct
{
    mutex_t       lock;   /* lock against the entry */
    sys_thread_t* list;   /* blocking list on the fd */
} file_t;

/*
 * Global data structure for interruptable io.
 * It must be initialized before any IO access.
 */
static file_t * fd_table = 0;
static int      fd_limit = 0;

/*
 * Initialize global data structure for non-blocking
 * close semantics for Solaris 2.6 and ealier.
 */
int InitializeIO(rlim_t limit)
{
    int i;

    fd_limit = (int) limit;

    fd_table = (file_t *) sysCalloc(fd_limit, sizeof(file_t));
    if (fd_table == 0) {
        return SYS_ERR;
    }

    for (i = 0; i < fd_limit; i++) {
        mutexInit(&(fd_table[i].lock));
    }

    return SYS_OK;
}

/*
 * Cleanup the data structure allocated as above.
 * For JDK 1.2, this function is not called ...
 */
void FinalizeIO() {
    int i;
    for (i = 0; i < fd_limit; i++) {
        mutexDestroy(&fd_table[i].lock);
    }
    sysFree(fd_table);
    fd_table = 0;
}

/*
 * Non-blocking close semantics on Solaris native thread.
 */
int sysClose(int fd)
{
    int ret;

    /* Check if it is valid fd. */
    if (fd >= 0 && fd < fd_limit) {
        file_t* file = &fd_table[fd];
        sys_thread_t *thread;
        sys_thread_t *next;

        /* Lock the corresponding fd. */
        mutexLock(&file->lock);

        /* Read the blocking list. */
        thread = file->list;

        /* Iterates the list and interrupt every thread in there. */
        while (thread) {
            /* This is the classic double-linked list operation. */
            if (thread->nextBlocked != thread) {
                next = thread->nextBlocked;

                next->prevBlocked = thread->prevBlocked;
                thread->prevBlocked->nextBlocked = next;
            } else {
                next = 0;
            }

            thread->nextBlocked = 0;
            thread->prevBlocked = 0;

            /*
             * Use current interruptable IO mechanism to
             * implement non-blocking closable IO.
             */
            sysThreadInterrupt(thread);

            thread = next;
        }

        file->list = 0;

        ret = close(fd);

        mutexUnlock(&file->lock);
    } else {
        /* It is not a valid fd. */
        errno = EBADF;
        ret = SYS_ERR;
    }

    return ret;
}

/*
 * Called before entering blocking IO. Enqueue the current
 * thread to the fd blocking list. Need fd lock.
 */
static void BeginIO(sys_thread_t* self, file_t* file) {
    mutexLock(&file->lock);

    if (!file->list) {
        file->list = self->nextBlocked = self->prevBlocked = self;
    } else {
        sys_thread_t* head = file->list;

        self->prevBlocked = head->prevBlocked;
        self->nextBlocked = head;
        head->prevBlocked->nextBlocked = self;
        head->prevBlocked = self;
    }
    mutexUnlock(&file->lock);
}

/*
 * Called after finishing blocking IO. Dequeue the current
 * thread from the blocking list. Note: It may be waken up
 * by thread interrupt or fd close operation.
 */
static ssize_t EndIO(sys_thread_t* self, file_t* file, ssize_t ret) {
    mutexLock(&file->lock);

    /*
     * Dequeue the current thread. It is classic double
     * linked list operation.
     */
#ifdef __linux__
    if (!sysThreadIsInterrupted(self, 1) && self->prevBlocked) {
#else
    if (self->prevBlocked) {
#endif
        if (self->nextBlocked != self) {
            self->prevBlocked->nextBlocked = self->nextBlocked;
            self->nextBlocked->prevBlocked = self->prevBlocked;
            file->list = self->nextBlocked;
        } else {
            file->list = 0;
        }
        self->nextBlocked = 0;
        self->prevBlocked = 0;
    } else {
#ifdef __linux__
        if (self->nextBlocked && self->prevBlocked) {
            if (self->nextBlocked != self) {
                self->prevBlocked->nextBlocked = self->nextBlocked;
                self->nextBlocked->prevBlocked = self->prevBlocked;
                file->list = self->nextBlocked;
            } else {
                file->list = 0;
            }
        }
        self->nextBlocked = 0;
        self->prevBlocked = 0;
#endif
        /* file got closed during blocking call */
        errno = EBADF;
        ret = SYS_ERR;
    }

    mutexUnlock(&file->lock);

    return ret;
}

/*
 * The following is a big macro used to implement the closable IO.
 * Note: It is also used by interruptable IO. If later we need to
 * deprecate interruptable IO, all we need to change the return
 * value and errno to EBADF instead of EINTR. The high level
 * routine will interpret it as IOException instead of
 * InterruptedIOException. No other change is needed.
 * The underlying mechanism is using the SIGUSR1 signal to wake up the
 * blocking thread.  This may cause severe conflicts with any other
 * libraries that also use SIGUSR1.
 */
#ifdef __linux__
#define INTERRUPT_IO(cmd) \
{\
    ssize_t ret = 0;\
    file_t* file;\
    sys_thread_t* self = sysThreadSelf();\
\
    if (fd < 0 || fd >= fd_limit) {\
        errno = EBADF;\
        return SYS_ERR;\
    }\
\
    file = &fd_table[fd];\
    BeginIO(self, file);\
\
    {\
        jmp_buf jmpbuf;\
\
        /*\
         * Register our intrHandler as a cleanup handler.  If we get\
         * interrupted (i.e. canceled), we longjmp out of this handler.\
         */\
        pthread_cleanup_push(intrHandler, NULL);\
        if (setjmp(jmpbuf) == 0) {\
            thr_setspecific(intrJmpbufkey, &jmpbuf);\
            pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);\
            ret = cmd;\
            pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);\
            thr_setspecific(intrJmpbufkey, NULL);\
        } else {\
           /* [jk] should/can we call sysThreadIsInterrupted(self, 1) here */

            self->interrupted = FALSE;\
            errno = EINTR;\
            ret = SYS_INTRPT;\
        }\
        /* Remove intrHandler without calling it. */\
        pthread_cleanup_pop(0);\
    }\
\
    return EndIO(self, file, ret);\
}
#else
#define INTERRUPT_IO(cmd) \
{\
    int ret = 0;\
    file_t* file;\
    sys_thread_t* self = sysThreadSelf();\
\
    if (fd < 0 || fd >= fd_limit) {\
        errno = EBADF;\
        return SYS_ERR;\
    }\
\
    file = &fd_table[fd];\
    BeginIO(self, file);\
\
    {\
        sigjmp_buf jmpbuf;\
        sigset_t omask;\
\
        thr_setspecific(sigusr1Jmpbufkey, &jmpbuf);\
        if (sigsetjmp(jmpbuf, 1) == 0) {\
            thr_sigsetmask(SIG_UNBLOCK, &sigusr1Mask, &omask);\
            ret = cmd;\
            thr_sigsetmask(SIG_SETMASK, &omask, NULL);\
        } else {\
            sysThreadIsInterrupted(self, TRUE);\
            errno = EINTR;\
            ret = SYS_INTRPT;\
        }\
    }\
\
    return EndIO(self, file, ret);\
}
#endif

#else /* CLOSEIO */

#define INTERRUPT_IO(cmd) \
    return cmd;

int sysClose(int fd) {
    return close(fd);
}

int InitializeIO(rlim_t limit)
{
    return SYS_OK;
}
#endif /* CLOSEIO */

/*
 * sys API for I/O
 */

size_t
sysRead(int fd, void *buf, unsigned int nBytes) {
    INTERRUPT_IO(read(fd, buf, nBytes))
}

size_t
sysWrite(int fd, const void *buf, unsigned int nBytes) {
    INTERRUPT_IO(write(fd, buf, nBytes))
}

int
sysSocket(int domain, int type, int protocol) {
    return socket(domain, type, protocol);
}

ssize_t
sysRecv(int fd, char *buf, int nBytes, int flags) {
    INTERRUPT_IO(recv(fd, buf, nBytes, flags))
}

ssize_t
sysSend(int fd, char *buf, int nBytes, int flags) {
    INTERRUPT_IO(send(fd, buf, nBytes, flags))
}
/*
int
sysClose(int fd) {
    INTERRUPT_IO(close(fd))
}
*/
jlong
sysSeek(int fd, jlong offset, int whence) {
    return lseek64_w(fd, offset, whence);
}

int
sysSetLength(int fd, jlong length) {
    return ftruncate64_w(fd, length);
}

int
sysSync(int fd) {
    /*
     * XXX: Is fsync() interruptible by the interrupt method?
     * Is so, add the TSD, sigsetjmp()/longjmp() code here.
     *
     * This probably shouldn't be throwing an error and should
     * be a macro.
     */
    int ret;
    if ((ret = fsync(fd)) == -1) {
    }
    return ret;
}

int
sysAvailable(int fd, jlong *pbytes) {
    jlong cur, end;
    int mode;

    if (sysFfileMode(fd, &mode) >= 0) {
        if (S_ISCHR(mode) || S_ISFIFO(mode) || S_ISSOCK(mode)) {
            /*
             * XXX: is the following call interruptible? If so, this might
             * need to go through the INTERRUPT_IO() wrapper as for other
             * blocking, interruptible calls in this file.
             */
            int n;
            if (ioctl(fd, FIONREAD, &n) >= 0) {
                *pbytes = n;
                return 1;
            }
        }
    }
    if ((cur = lseek64_w(fd, 0L, SEEK_CUR)) == -1) {
        return 0;
    } else if ((end = lseek64_w(fd, 0L, SEEK_END)) == -1) {
        return 0;
    } else if (lseek64_w(fd, cur, SEEK_SET) == -1) {
        return 0;
    }
    *pbytes = end - cur;
    return 1;
}

/* IO routines that take in a FD object */

int
sysTimeout(int fd, long timeout) {
#ifndef USE_SELECT
    struct pollfd pfd;

#ifdef __linux__
    jlong end_time = sysTimeMillis() + (jlong) timeout;
    volatile jlong to = (jlong) timeout;
#endif

    pfd.fd = fd;
    pfd.events = POLLIN;

#ifdef __linux__
    INTERRUPT_IO(__extension__ ({
        int __result;
        do {
            pthread_setcanceltype(PTHREAD_CANCEL_ASYNCHRONOUS, NULL);
            __result = poll(&pfd, 1, ((int)to));
            pthread_setcanceltype(PTHREAD_CANCEL_DEFERRED, NULL);
        } while (__result == -1 && errno == EINTR &&
                 (to = end_time - sysTimeMillis()) > 0 &&
                 ((pfd.revents & (POLLERR | POLLHUP | POLLNVAL)) == 0));
        if (pfd.revents & (POLLERR | POLLHUP | POLLNVAL)) {
            __result = -1;
            errno = EBADF;
        }
        (__result == -1 && errno == EINTR) ? 0 : __result;
    }))
#else
    INTERRUPT_IO(poll(&pfd, 1, (int)timeout))
#endif
#else
    fd_set tbl;
    struct timeval t;

    t.tv_sec = timeout / 1000;
    t.tv_usec = (timeout % 1000) * 1000;

    FD_ZERO(&tbl);
    FD_SET(fd, &tbl);

#ifdef __linux__
    INTERRUPT_IO(TEMP_FAILURE_RETRY(select(fd + 1, &tbl, 0, 0, &t)))
#else
    INTERRUPT_IO(select(fd + 1, &tbl, 0, 0, &t))
#endif
#endif
}


/*
 * sys API for networking
 */

long
sysSocketAvailable(int fd, jint *pbytes) {
    long ret = 1;
    /*
     * An ILP64 port of this code should pass the address of a local int
     * to the ioctl and then convert that to jint with any error handling
     * required for overflows, if overflow is possible.
     */

    /*
     * XXX: is the following call interruptible? If so, this might
     * need to go through the INTERRUPT_IO() wrapper as for other
     * blocking, interruptible calls in this file.
     */
    if (fd < 0 || ioctl(fd, FIONREAD, pbytes) < 0) {
        ret = 0;
    }
    return ret;
}

int
sysListen(int fd, int count) {
   return listen(fd, count);
}

int
sysConnect(int fd, struct sockaddr *addr, int size)  {
    INTERRUPT_IO(connect(fd, addr, size))
}

int
sysBind(int fd, struct sockaddr *addr, int size)  {
    INTERRUPT_IO(bind(fd, addr, size))
}

int
sysAccept(int fd, struct sockaddr *him, int *len) {
    INTERRUPT_IO(accept(fd, him, (uint *)len))
}

int
sysGetSockName(int fd, struct sockaddr *him, int *len) {
    return getsockname(fd, him, (uint *)len);
}

int
sysSocketClose(int fd) {
    return sysClose(fd);
}

int
sysSocketShutdown(int fd, int howto) {
    return shutdown(fd, howto);
}

int
sysGetSockOpt(int fd, int level, int optname, char *optval, int *optlen) {
    return getsockopt(fd, level, optname, optval, optlen);
}

int
sysSetSockOpt(int fd, int level, int optname, const char *optval, int optlen) {
    return setsockopt(fd, level, optname, optval, optlen);
}

int
sysGetHostName(char *hostname, int namelen) {
  return gethostname(hostname, namelen);
}

struct hostent *
sysGetHostByAddr(const char *hostname, int len, int type) {
  return gethostbyaddr(hostname, len, type);
}

struct hostent *
sysGetHostByName(char *hostname) {
  return gethostbyname(hostname);
}

struct protoent *
sysGetProtoByName(char* name) {
    return getprotobyname(name);
}

/*
 * Routines to do datagrams
 */
ssize_t
sysSendTo(int fd, char *buf, int len,
          int flags, struct sockaddr *to, int tolen) {
    INTERRUPT_IO(sendto(fd, buf, len, flags, to, tolen))
}

ssize_t
sysRecvFrom(int fd, char *buf, int nBytes,
            int flags, struct sockaddr *from, int *fromlen) {
    INTERRUPT_IO(recvfrom(fd, buf, nBytes, flags, from, (uint *)fromlen))
}
