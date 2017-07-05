/*
 * Copyright (c) 1999, 2000, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Implementation of notposix.h on Linux.
 */

#include <pthread.h>
#include <signal.h>
#include <string.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <time.h>

#include "hpi_impl.h"
#include "monitor_md.h"
#include "threads_md.h"
#include "np.h"

#undef LOG_THREADS

/* Global lock used when calling np_suspend and np_resume */
static pthread_mutex_t sr_lock;

/* Semaphore used to acknowledge when the handler has received HANDLER_SIG */
static sem_t sr_sem;

/* The tid of the thread being suspended/resumed */
static sys_thread_t *sr_tid;

int sr_sigsusp;
int sr_sigresu;

static void prtsigset(char *s, sigset_t *set)
{
    int sig;
    dprintf(2, "%s:", s);
    for (sig = 1; sig < _NSIG; sig++) {
        if (sigismember(set, sig)) {
            dprintf(2, " %d", sig);
        }
    }
    dprintf(2, "\n");
}

/*
 * Handler function invoked when a thread's execution is suspended
 * We have to be careful that only async-safe functions are
 * called here. I'm not even sure if calling sysThreadSelf is safe so
 * we temporarily stash SP in a global variable instead.
 */
static void
#ifdef SA_SIGINFO
susp_handler(int sig, siginfo_t* info, void* arg)
#else
susp_handler(int sig)
#endif
{
    sys_thread_t *tid = sr_tid;
    sigset_t set;
    /* Save the current SP */
    tid->sp = &tid;
    sem_post(&sr_sem);
    sigfillset(&set);
    sigdelset(&set,(sr_sigresu));
    /* block until we receive resume signal. */
    sigsuspend(&set);
}

static void
#ifdef SA_SIGINFO
resu_handler(int sig, siginfo_t* info, void* arg)
#else
resu_handler(int sig)
#endif
{
    return;
}

/*
 * Initialize signal handlers for suspend and resume}.
 */
int
np_initialize()
{
    struct sigaction act;
    char *s;
    int err;

    /* Signal numbers used to suspend and resume */
#if __GLIBC__ == 2 && __GLIBC_MINOR__ == 0
#ifdef SIGUNUSED
    sr_sigsusp = SIGUNUSED;
#else
    sr_sigsusp = SIGLOST;
#endif
#ifdef SIGPWR
    sr_sigresu = SIGPWR;
#else
    sr_sigresu = SIGXFSZ;
#endif
#else
    /* use real time signals */
    /* currently __SIGRTMIN, +1, +2 are all used by LinuxThreads */
    sr_sigsusp = SIGRTMIN + 3;
    sr_sigresu = SIGRTMIN + 4;
#endif

    /* Set up signal handler for suspend and resume */
#if defined(SA_SIGINFO) && !defined(__sparc__)
    act.sa_handler = 0;
    act.sa_sigaction = susp_handler;
#else
    act.sa_handler = (__sighandler_t) susp_handler;
#endif
#ifdef SA_SIGINFO
    act.sa_flags = SA_RESTART | SA_SIGINFO;
#else
    act.sa_flags = SA_RESTART;
#endif
    sigfillset(&act.sa_mask);
    if (sigaction(sr_sigsusp, &act, 0) == -1) {
        return -1;
    }
#if defined(SA_SIGINFO) && !defined(__sparc__)
    act.sa_handler = 0;
    act.sa_sigaction = resu_handler;
#else
    act.sa_handler = (__sighandler_t) resu_handler;
#endif
#ifdef SA_SIGINFO
    act.sa_flags = SA_SIGINFO;
#else
    act.sa_flags = 0;
#endif
    sigfillset(&act.sa_mask);
    if (sigaction(sr_sigresu, &act, 0) == -1) {
        return -1;
    }

    /* Initialize semaphore used by np_{suspend/resume} */
    if (sem_init(&sr_sem, 0, 0) == -1) {
        return SYS_ERR;
    }

    /* Initialize mutex used by np_{suspend/resume} */
    err = mutexInit(&sr_lock);
    sysAssert(err == 0);

    return SYS_OK;
}

int
np_initial_suspend(sys_thread_t* tid)
{
    int count;

    tid->selfsuspended = (tid == sysThreadSelf());
    sysAssert(tid->selfsuspended);

    count = tid->suspend_count++;
    sysAssert(count == 0);

#ifdef LOG_THREADS
    dprintf(2,
            "[Initial self-suspend [tid = %ld, sys_thread = %ld]\n",
            pthread_self(), tid->sys_thread);
#endif

    /* Order should not matter but doing the post first should be faster */
    sem_post(&tid->sem_suspended);
    do {
        sem_wait(&tid->sem_selfsuspend);
    } while (tid->selfsuspended); /* paranoid */
    return 0;
}


int
np_suspend(sys_thread_t *tid)
{
    int count, ret = 0;

    int err = mutexLock(&sr_lock);
    sysAssert(err == 0);

    tid->selfsuspended = (tid == sysThreadSelf());

    count = tid->suspend_count++;
#ifdef LOG_THREADS
    dprintf(2, "[Suspending fromtid = %ld, tid = %ld, pid = %d, count = %d]\n",
            pthread_self(), tid->sys_thread, tid->lwp_id, count);
#endif
    if (count == 0) {
        if (tid->selfsuspended) {
#ifdef LOG_THREADS
            dprintf(2,
                    "[Self-suspending [tid = %ld, sys_thread = %ld]\n",
                    pthread_self(), tid->sys_thread);
#endif
            mutexUnlock(&sr_lock);
            do {
                sem_wait(&tid->sem_selfsuspend);
            } while (tid->selfsuspended);
            /* [jk] What is the correct return value here?
               There was no error, but when we return the thread
               has already been resumed. */
            return SYS_OK;

        } else {
            sr_tid = tid;
            ret = pthread_kill(tid->sys_thread, sr_sigsusp);
            if (ret == 0) {
                sem_wait(&sr_sem);
            }
#ifdef LOG_THREADS
            dprintf(2,
                    "[Suspended fromtid = %ld, pthread_kill(%ld, %d) = %d]\n",
                    pthread_self(), tid->sys_thread, sr_sigsusp, ret);
#endif
        }
    }

    err = mutexUnlock(&sr_lock);
    sysAssert(err == 0);

    return ret == 0 ? SYS_OK : SYS_ERR;
}

int
np_continue(sys_thread_t *tid)
{
    int count, ret = 0;

    int err = mutexLock(&sr_lock);
    sysAssert(err == 0);

    count = --tid->suspend_count;
#ifdef LOG_THREADS
    dprintf(2, "[Resuming fromtid = %ld, tid = %ld, pid = %d, count = %d]\n",
            pthread_self(), tid->sys_thread, tid->lwp_id, count);
#endif
    if (count == 0) {
        if (tid->selfsuspended) {
            tid->selfsuspended = 0;
            sem_post(&tid->sem_selfsuspend);
        } else {
            sr_tid = tid;
            ret = pthread_kill(tid->sys_thread, sr_sigresu);
        }
#ifdef LOG_THREADS
        dprintf(2, "[Resumed fromtid = %ld, pthread_kill(%ld, %d) = %d]\n",
                pthread_self(), tid->sys_thread, sr_sigresu, ret);
#endif
    } else if (count < 0) {
        /* Ignore attempts to resume a thread that has not been suspended */
        tid->suspend_count = 0;
    }

     err = mutexUnlock(&sr_lock);
     sysAssert(err == 0);

     return ret == 0 ? SYS_OK : SYS_ERR;
}

/*
 * Get the stack base and size.
 */
int
np_stackinfo(void **addr, long *size)
{
    /* For now assume stack is 2 meg, from internals.h. */
#define STACK_SIZE (2 * 1024 * 1024)
    void *p;
    char *sp = (char *)&p;  /* rougly %esp */

    *addr = (void *)(((unsigned long)sp | (STACK_SIZE-1))+1) - 1;
    *size = STACK_SIZE;

    return SYS_OK;
}

typedef unsigned long ulong_t;
#define VALID_SP(sp, bottom, top) \
       (((ulong_t)(sp)) < ((ulong_t)(bottom)) && ((ulong_t)(sp)) > ((ulong_t)(top)))

/*
 * Go into single threaded mode for GC.
 */
int
np_single()
{
    sys_thread_t *tid;
    pthread_t me = pthread_self();
    int i;

#ifdef LOG_THREADS
    dprintf(2, "[Entering np_single: thread count = %d]\n", ActiveThreadCount);
#endif
    /* Stop all other threads. */
    tid = ThreadQueue;
    for (i = 0; i < ActiveThreadCount && tid != 0; i++) {
        if ((tid->sys_thread != me) && (tid->state != SUSPENDED)) {
            np_suspend(tid);
            sysAssert(VALID_SP(tid->sp, tid->stack_bottom, tid->stack_top));
            tid->onproc = FALSE; /* REMIND: Might not need this */
        }
        tid = tid->next;
    }
#ifdef LOG_THREADS
    dprintf(2, "[Leaving np_single]\n");
#endif
    return SYS_OK;
}

/*
 * Per thread initialization.
 */
void
np_initialize_thread(sys_thread_t *tid)
{
    sigset_t set;

    /* Block SIGQUIT so that it can be handled by the SIGQUIT handler thread */
    sigemptyset(&set);
    sigaddset(&set, SIGQUIT);
    pthread_sigmask(SIG_BLOCK, &set, 0);
    /* Set process id */
    tid->lwp_id = getpid();
    tid->suspend_count = 0;

    /* Semaphore used for self-suspension */
    sem_init(&tid->sem_selfsuspend, 0, 0);
    tid->selfsuspended = 0;

#ifdef LOG_THREADS
    dprintf(2, "[Init thread, tid = %ld, pid = %d, base = %p, size = %lu]\n",
            pthread_self(), tid->lwp_id, tid->stack_bottom, tid->stack_size);
#endif
}

void
np_free_thread(sys_thread_t *tid)
{
    sem_destroy(&tid->sem_selfsuspend);
}

/*
 * Recover from single threaded mode after GC.
 */
void
np_multi()
{
    int i;
    sys_thread_t *tid;
    pthread_t me = pthread_self();

    tid = ThreadQueue;
    for (i = 0; i < ActiveThreadCount && tid != 0; i++) {
        if ((tid->sys_thread != me) && (tid->state != SUSPENDED)) {
            np_continue(tid);
        }
        tid = tid->next;
    }
}

void
np_profiler_init(sys_thread_t *tid)
{
}

int
np_profiler_suspend(sys_thread_t *tid)
{
    return np_suspend(tid);
}

int
np_profiler_continue(sys_thread_t *tid)
{
    return np_continue(tid);
}

bool_t
np_profiler_thread_is_running(sys_thread_t *tid)
{
    return TRUE;
}
