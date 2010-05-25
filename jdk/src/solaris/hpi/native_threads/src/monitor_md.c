/*
 * Copyright (c) 1994, 1998, Oracle and/or its affiliates. All rights reserved.
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
 * Monitor implementation for Native Solaris threads
 *
 * Java Monitors are implemented using one solaris mutex and two
 * condition variables. Because solaris mutex is not re-entrant we
 * cannot simply have a monitor map to a mutex as re-entering a monitor
 * would deadlock an application.
 *
 * Monitor is implemented using:
 *   mutex_t             mutex;
 *   condvar_t           cv_monitor;
 *   condvar_t           cv_waiters;
 *
 * mutex protects the monitor state.
 * cv_monitor is the condtion variable used along with mutex for
 *     supporting wait and notify on the monitor.
 * cv_waiters is used for all the threads waiting to acquire the monitor
 *     lock.
 *
 * All of the sysMonitorXXX() functions that are passed a sys_mon_t
 * assume that they get something nonnull, and only check when debugging.
 */

#include "hpi_impl.h"

#include "threads_md.h"
#include "monitor_md.h"

#include <errno.h>
#include <limits.h>

static mutex_t contention_count_mutex;

void initializeContentionCountMutex()
{
    mutexInit(&contention_count_mutex);
}

/*
 * Operations on monitors
 */
/*
 * Return the size of the lib-dependent portion of monitors.  This
 * is done this way so that monitors can be all of one piece,
 * without paying the penalty of an extra level of indirection on
 * each sys_mon reference.  This is not how it is done for threads
 * and it might be a good idea to use a pointer the same way that
 * threads do.
 */
size_t
sysMonitorSizeof()
{
    return sizeof(struct sys_mon);
}

int
sysMonitorInit(sys_mon_t *mid)
{
    int ret;

    sysAssert(mid != SYS_MID_NULL);
    ret = mutexInit(&mid->mutex);
    ret = (ret == SYS_OK ? condvarInit(&mid->cv_monitor) : ret);

    mid->entry_count = 0;
    mid->monitor_owner = SYS_THREAD_NULL;
    mid->contention_count = 0;
    INIT_MONITOR_WAIT_QUEUE( mid->mwait_queue );

    return ret;
}

/*
 * Free any system-dependent resources held by monitors.  There is
 * nothing to be done for native Solaris mutexes or condition variables.
 */
int
sysMonitorDestroy(sys_mon_t *mid)
{
    sysAssert(mid != SYS_MID_NULL);

    return SYS_OK;
}

static void
enqueue_me(monitor_waiter_t *mp, monitor_wait_queue_t *queue,
           sys_thread_t *self)
{
    /*
     * Order does not matter here. It is more convenient to
     * enqueue ourselves at the head of the list, so we do so.
     */
    mp->waiting_thread = self;
    mp->next = queue->head;
    mp->prev = &(queue->head);
    if ( queue->head != NULL ){
        queue->head->prev = &(mp->next);
    }
    queue->head = mp;
    queue->count++;
}

static void
dequeue_me(monitor_waiter_t *mp, monitor_wait_queue_t *queue)
{
    queue->count--;
    *(mp->prev) = mp->next;
    if (mp->next != NULL ){
        mp->next->prev = mp->prev;
    }
    mp->next = NULL;
}

int
sysMonitorEnter(sys_thread_t *self, sys_mon_t *mid)
{
    int err;

    sysAssert(mid != SYS_MID_NULL);
    err = mutex_trylock(&mid->mutex);
    if (err == 0) { /* no one owns it */
        mid->monitor_owner = self;
        mid->entry_count = 1;
        return SYS_OK;
    } else if (err == EBUSY) { /* it's already locked */
        if (mid->monitor_owner == self) {
            mid->entry_count++;
            return SYS_OK;
        } else {
            self->mon_enter = mid;
            /* block on it */
            if (profiler_on) {
                VM_CALL(monitorContendedEnter)(self, mid);
                mutexLock(&contention_count_mutex);
                mid->contention_count++;
                mutexUnlock(&contention_count_mutex);
            }
            mutex_lock(&mid->mutex);
            mid->monitor_owner = self;
            mid->entry_count = 1;
            self->mon_enter = NULL;
            if (profiler_on) {
                mutexLock(&contention_count_mutex);
                mid->contention_count--;
                mutexUnlock(&contention_count_mutex);
                VM_CALL(monitorContendedEntered)(self, mid);
            }
            return SYS_OK;
        }
    } else {
        sysAssert(err == 0);
        return SYS_ERR;
    }
}

/*
 * Return true if we currently own this monitor (and threads have been
 * initialized.
 */
bool_t
sysMonitorEntered(sys_thread_t *self, sys_mon_t *mid)
{
    sysAssert(mid != SYS_MID_NULL);

    /* We can only have locked monitors if threads have been initialized */
    return (mid->monitor_owner == self);
}

int
sysMonitorExit(sys_thread_t *self, sys_mon_t *mid)
{
    sysAssert(mid != SYS_MID_NULL);

    if (mid->monitor_owner == self) {
        sysAssert(mid->entry_count > 0);
        if (--mid->entry_count == 0) {
            mid->monitor_owner = SYS_THREAD_NULL;
            if (!mid->contention_count || !profiler_on) {
                mutex_unlock(&mid->mutex);
            } else {
                mutex_unlock(&mid->mutex);
                VM_CALL(monitorContendedExit)(self, mid);
            }
        }
        return SYS_OK;
    } else {
        return SYS_ERR;
    }
}

int
sysMonitorNotify(sys_thread_t *self, sys_mon_t *mid)
{
    sysAssert(mid != SYS_MID_NULL);
    if (self == mid->monitor_owner) {
        if (ANY_WAITING(mid->mwait_queue)) {
            /* If there is someone doing a monitor wait */
            condvarSignal(&(mid->cv_monitor));
        }
        return SYS_OK;
    } else
        return SYS_ERR;
}

int
sysMonitorNotifyAll(sys_thread_t *self, sys_mon_t *mid)
{
    sysAssert(mid != SYS_MID_NULL);
    if (self == mid->monitor_owner) {
        if (ANY_WAITING(mid->mwait_queue)) {
            /* If there is someone doing a monitor wait */
            condvarBroadcast(&(mid->cv_monitor));
        }
        return SYS_OK;
    } else
        return SYS_ERR;
}

int
sysMonitorWait(sys_thread_t *self, sys_mon_t *mid, jlong millis)
{
    int ret = SYS_OK;
    monitor_waiter_t me;
    sysAssert(mid != SYS_MID_NULL);

    if (self != mid->monitor_owner) {
        return SYS_ERR;
    }
    if (sysThreadIsInterrupted(self, TRUE)) {
        return SYS_INTRPT;
    }

    /* Prepare to wait: drop mutex ownership */
    sysAssert(self->monitor_entry_count == 0);
    sysAssert(self->mon_wait == 0);
    self->mon_wait = (sys_mon_t *) mid;
    self->monitor_entry_count = mid->entry_count;
    mid->entry_count = 0;
    mid->monitor_owner = SYS_THREAD_NULL;

    /* Add myself to the monitor waitq */
    enqueue_me(&me, &mid->mwait_queue, self);
    if (millis == SYS_TIMEOUT_INFINITY) {
        ret = condvarWait(&mid->cv_monitor, &mid->mutex, CONDVAR_WAIT);
    } else {
        ret = condvarTimedWait(&mid->cv_monitor, &mid->mutex, millis,
                               CONDVAR_WAIT);
    }
    dequeue_me(&me, &mid->mwait_queue);

    sysAssert(mid->monitor_owner == NULL);
    sysAssert(mid->entry_count == 0);
    mid->monitor_owner = self;
    mid->entry_count = self->monitor_entry_count;
    self->monitor_entry_count = 0;
    self->mon_wait = 0;

    /* Did we get interrupted in mid-wait?  (IS THIS THE RIGHT PLACE?) */
    if (sysThreadIsInterrupted(self, TRUE)) {
        return SYS_INTRPT;
    }

    return ret;
}

static int
dumpWaitingQueue(monitor_wait_queue_t *queue, sys_thread_t **waiters, int sz)
{
    int n;
    monitor_waiter_t * waiter;
    if (queue == NULL || ( waiter = queue->head ) == NULL ) {
        return 0;
    }
    for (n = 0; waiter != 0; waiter = waiter->next, n++, sz--) {
        if (sz > 0) {
            waiters[n] = waiter->waiting_thread;
        }
    }
    return n;
}

typedef struct {
    sys_mon_t *mid;
    sys_thread_t **waiters;
    int sz;
    int nwaiters;
} wait_info;

static int
findWaitersHelper(sys_thread_t *t, void *arg)
{
    wait_info * winfo = (wait_info *) arg;
    if (t->mon_enter == winfo->mid) {
        if (winfo->sz > 0) {
            winfo->waiters[winfo->nwaiters] = t;
        }
        winfo->sz--;
        winfo->nwaiters++;
    }
    return SYS_OK;
}

int
sysMonitorGetInfo(sys_mon_t *mid, sys_mon_info *info)
{
    wait_info winfo;

    sysAssert(mid != SYS_MID_NULL);
    info->owner = mid->monitor_owner;
    if (mid->monitor_owner) {
        info->entry_count = mid->entry_count;
    }

    winfo.mid = mid;
    winfo.nwaiters = 0;
    winfo.waiters = info->monitor_waiters;
    winfo.sz = info->sz_monitor_waiters;
    sysThreadEnumerateOver(findWaitersHelper, (void *) &winfo);
    info->n_monitor_waiters = winfo.nwaiters;

    info->n_condvar_waiters = dumpWaitingQueue(&mid->mwait_queue,
                                               info->condvar_waiters,
                                               info->sz_condvar_waiters);

    return SYS_OK;
}


bool_t
sysMonitorInUse(sys_mon_t * mon)
{
    return mon->monitor_owner != 0 ||
        mon->mwait_queue.count != 0;
}

sys_thread_t *
sysMonitorOwner(sys_mon_t *mon)
{
    return mon->monitor_owner;
}
