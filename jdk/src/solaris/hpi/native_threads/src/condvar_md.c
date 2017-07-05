/*
 * Copyright (c) 1994, 2000, Oracle and/or its affiliates. All rights reserved.
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
 * Condition variable HPI implementation for Solaris
 */

#include <errno.h>
#include <time.h>
#include <setjmp.h>
#include <signal.h>
#include <limits.h>

#include "hpi_impl.h"

#include "condvar_md.h"
#include "mutex_md.h"
#include "threads_md.h"

int
condvarInit(condvar_t *condvar)
{
    int err;

#ifdef USE_PTHREADS
    err = pthread_cond_init(&condvar->cond, NULL);
#else
    err = cond_init(&condvar->cond, USYNC_THREAD, 0 /* ignored */);
#endif
    condvar->counter = 0;
    return (err == 0 ? SYS_OK : SYS_ERR);
}

int
condvarDestroy(condvar_t *condvar)
{
    int err;

#ifdef __linux__
    err = pthread_cond_destroy((cond_t *) &condvar->cond);
#else
    err = cond_destroy((cond_t *) condvar);
#endif
    return (err == 0 ? SYS_OK : SYS_ERR);
}

int
condvarWait(condvar_t *condvar, mutex_t *mutex, thread_state_t wtype)
{
    sigjmp_buf jmpbuf;
    int err;

    sys_thread_t *self = sysThreadSelf();
    /*
     * There is no threads interface to get a thread's state. So, instead,
     * we use this hack so that the debugger agent can get at this thread's
     * state. Of course, this is not very reliable, but when a thread goes
     * to sleep, it *will* be reported as sleeping. During the transition
     * from running to sleep, it may be incorrectly reported, since the
     * setting of the state here is not atomic with the voluntary sleep.
     * The better fix is to extend the Solaris threads interface and have
     * the debugger agent call this interface OR to use libthread_db for
     * intra-process state reporting.
     *
     * Now, condition variables are used either for waiting to enter a
     * monitor (MONITOR_WAIT) or to execute a "wait()" method when already
     * holding a monitor (CONDVAR_WAIT). So, when condvarWait() is called
     * it could be to wait for a monitor or for a condition within a
     * monitor.  This is indicated by the "wtype" argument to condvarWait().
     * This type is set in the thread state before going to sleep.
     */
    self->state = wtype;

#ifdef __linux__
     /*
      * Register our intrHandler as a cleanup handler.  If we get
      * interrupted (i.e. canceled), we longjmp out of this handler.
      */
     pthread_cleanup_push(intrHandler, NULL);
     if (setjmp(jmpbuf) == 0) {
         /*
          * Set the jmp buf and enable cancellation.
          */
         thr_setspecific(intrJmpbufkey, &jmpbuf);
         pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);

         /*
          * Note: pthread_cond_wait is _not_ interruptible on Linux
          */
#else
    thr_setspecific(sigusr1Jmpbufkey, &jmpbuf);
    if (sigsetjmp(jmpbuf, 1) == 0) {
        sigset_t osigset;

        thr_sigsetmask(SIG_UNBLOCK, &sigusr1Mask, &osigset);
again:
#endif
        err = cond_wait((cond_t *) condvar, (mutex_t *) mutex);
        switch(err) {
        case 0:
            err = SYS_OK;
            break;
#ifndef __linux__
        case EINTR: /* Signals other than USR1 were received. */
            goto again;
#endif
        default:
            err = SYS_ERR;
        }
#ifdef __linux__
       /*
        * Disable cancellation and clear the jump buf.
        */
        pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
        thr_setspecific(intrJmpbufkey, NULL);
#else
        thr_sigsetmask(SIG_SETMASK, &osigset, NULL);
#endif
    } else {
        /*
         * we've received a SIGUSR1 to interrupt our wait. We just return
         * and something above use notices the change.
         * clear the jump buf just to be paranoid.
         */
#ifndef __linux__
         thr_setspecific(sigusr1Jmpbufkey, NULL);
#endif
         err = SYS_INTRPT;
    }
#ifdef __linux__
    pthread_cleanup_pop(0);
#endif
    /*
     * After having woken up, change the thread state to RUNNABLE, since
     * it is now runnable.
     */
    self->state = RUNNABLE;

    return err;
}

/*
 * Returns 0 if condition variable became true before timeout expired.
 * Returns 1 if timeout expired first.
 * Returns <0 if wait fails for any other reason.
 */
int
condvarTimedWait(condvar_t *condvar, mutex_t *mutex,
    jlong millis, thread_state_t wtype)
{
#ifdef __linux__
    jmp_buf jmpbuf;
#else
    sigjmp_buf jmpbuf;
#endif
    int err;
    struct timespec timeout;
    sys_thread_t *self;
    jlong end_time;

    if (millis < 0)
        return SYS_ERR;

    if (millis > (jlong)INT_MAX) {
        return condvarWait(condvar, mutex, wtype);
    }

    end_time = sysTimeMillis() + millis;

    self = sysThreadSelf();
    self->state = wtype;

#ifdef __linux__
     /*
      * Register our intrHandler as a cleanup handler.  If we get
      * interrupted (i.e. canceled), we longjmp out of this handler.
      */
     pthread_cleanup_push(intrHandler, NULL);
     if (setjmp(jmpbuf) == 0) {
         /*
          * Set the jmp buf and enable cancellation.
          */
         thr_setspecific(intrJmpbufkey, &jmpbuf);
         pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);

         /*
          * Calculate an absolute timeout value.
          */
       timeout.tv_sec = end_time / 1000;
       timeout.tv_nsec = (end_time % 1000) * 1000000;

      again:
#else
    thr_setspecific(sigusr1Jmpbufkey, &jmpbuf);
    if (sigsetjmp(jmpbuf, 1) == 0) {
        sigset_t osigset;

        thr_sigsetmask(SIG_UNBLOCK, &sigusr1Mask, &osigset);

    again:
        timeout.tv_sec = end_time / 1000;
        timeout.tv_nsec = (end_time % 1000) * 1000000;
#endif
        err = cond_timedwait((cond_t *)condvar, (mutex_t *)mutex, &timeout);
        switch(err) {
        case 0:
            err = SYS_OK;
            break;
        case EINTR: /* Signals other than USR1 were received. */
            if (sysTimeMillis() < end_time) {
                goto again;
            }
            /*FALLTHRU*/
#ifdef USE_PTHREADS
        case ETIMEDOUT:
#else
        case ETIME:
#endif
            err = SYS_TIMEOUT;
            break;
        default:
            err = SYS_ERR;
        }
#ifdef __linux__
        pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
        thr_setspecific(intrJmpbufkey, NULL);
#else
        thr_sigsetmask(SIG_SETMASK, &osigset, NULL);
#endif
    } else {
        /*
         * we've received a SIGUSR1 to interrupt our wait. We just return
         * and something above use notices the change.
         * clear the jump buf just to be paranoid.
         */
#ifndef __linux__
         thr_setspecific(sigusr1Jmpbufkey, NULL);
#endif
         err = SYS_INTRPT;
    }
#ifdef __linux__
     /* Remove intrHandler without calling it. */
     pthread_cleanup_pop(0);

     sysAssert(pthread_mutex_trylock(mutex) == EBUSY);

     /*
      * After having woken up, change the thread state to RUNNABLE, since
      * it is now runnable.
      */
#endif
    self->state = RUNNABLE;
    return err;
}

int
condvarSignal(condvar_t *condvar)
{
    int err;

    err = cond_signal((cond_t *) condvar);
    condvar->counter++;
    return (err == 0 ? SYS_OK : SYS_ERR);
}

int
condvarBroadcast(condvar_t *condvar)
{
    int err;

    err = cond_broadcast((cond_t *) condvar);
    condvar->counter++;
    return (err == 0 ? SYS_OK : SYS_ERR);
}
