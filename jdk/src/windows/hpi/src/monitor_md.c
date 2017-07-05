/*
 * Copyright (c) 1994, 2002, Oracle and/or its affiliates. All rights reserved.
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
 * Win32 implementation of Java monitors
 */

#include <windows.h>

#include "hpi_impl.h"

#include "threads_md.h"
#include "monitor_md.h"

/*
 * Use this information to improve performance for single CPU machine.
 */
static int systemIsMP;

static mutex_t semaphore_init_mutex;
static mutex_t *semaphore_init_mutex_p = NULL;
/*
 * Create and initialize monitor. This can be called before threads have
 * been initialized.
 */
int
sysMonitorInit(sys_mon_t *mid)
{
    sysAssert(mid != SYS_MID_NULL);

    if (semaphore_init_mutex_p == NULL) {
        systemIsMP = sysGetSysInfo()->isMP;
        mutexInit(&semaphore_init_mutex);
        semaphore_init_mutex_p = &semaphore_init_mutex;
    }

    mid->atomic_count = -1;     /* -1 for no thread, 0 means 1 thread */
    mid->semaphore = NULL;      /* No semaphore until needed */
    mid->monitor_owner  = SYS_THREAD_NULL;
    mid->entry_count    = 0;    /* Recursion count */
    mid->monitor_waiter = 0;    /* First waiting thread */
    mid->waiter_count   = 0;    /* Count of waiting and wake-up thread */

    return SYS_OK;
}

/*
 * Free any system-dependent resources held by monitors.  On Win32 this
 * means releasing the critical section (mutex) and condition variable
 * that are part of each monitor.
 */
int
sysMonitorDestroy(sys_mon_t *mid)
{
    sysAssert(mid != SYS_MID_NULL);

    CloseHandle(mid->semaphore);

    return SYS_OK;
}

static void initializeSemaphore(HANDLE *pSema)
{
    mutexLock(semaphore_init_mutex_p);
    if (*pSema == NULL) {
        *pSema = CreateSemaphore(0,0,1,0);
    }
    mutexUnlock(semaphore_init_mutex_p);
}

/*
 * Take ownership of monitor. This can be called before threads have
 * been initialized, in which case we do nothing since locked monitors
 * are not yet needed.

 * The actual code is split into two functions, the assembler routine
 * handles the fast path, while the C routine handle the slow path,
 * including the lazy initialization of monitor semaphore.
 *
 * REMIND: It is EXTREMELY RISKY to change any of the code without
 * thorough understanding of the system, compiler and call convention.
 */

static int __fastcall sysMonitorEnter2(sys_thread_t *self, sys_mon_t *mid)
{
    if (mid->semaphore == NULL) {
        initializeSemaphore(&(mid->semaphore));
        if (mid->semaphore == NULL) {
            return SYS_NORESOURCE;
        }
    }

    self->enter_monitor = mid;
    if (profiler_on) {
        VM_CALL(monitorContendedEnter)(self, mid);
    }
    WaitForSingleObject(mid->semaphore, INFINITE);
    self->enter_monitor = NULL;

    mid->monitor_owner = self;
    mid->entry_count = 1;

    if (profiler_on) {
        VM_CALL(monitorContendedEntered)(self, mid);
    }
    return SYS_OK;
}

/*
 * The following assembler routine is highly compiler specific.
 * Because of the complexity, there is no debug error check.
 */

#ifndef _WIN64
int __cdecl
sysMonitorEnter(sys_thread_t *self, sys_mon_t *mid)
{
    __asm
    {
        mov edx, dword ptr [esp+8]; // load mid
        mov ecx, dword ptr [esp+4]; // load self
        mov eax, dword ptr [edx+8]; // load mid->monitor_owner

        cmp eax, ecx;               // if ( self == mid->monitor_owner )
        je RECURSION;               // goto RECURSION;

        mov eax, dword ptr [systemIsMP];
        test eax, eax;
        jne MPM;

        inc dword ptr [edx];        /* atomic increment mid->atomic_count */
        jne ACQUIRE_SEMAPHORE;      /* if there is an owner already */

        mov dword ptr [edx+8], ecx; /* mid->monitor_owner = self */
        mov dword ptr [edx+12], 1;  /* mid->entry_count = 1 */
        xor eax, eax;               /* return SYS_OK */
        ret;

MPM:
        lock inc dword ptr [edx];   /* atomic increment mid->atomic_count */
        jne ACQUIRE_SEMAPHORE;      /* if there is an owner already */

        mov dword ptr [edx+8], ecx; /* mid->monitor_owner = self */
        mov dword ptr [edx+12], 1;  /* mid->entry_count = 1 */
        xor eax, eax;               /* return SYS_OK */
        ret;

RECURSION:
        inc dword ptr [edx+12];     /* Increment mid->entry_count */
        xor eax, eax;               /* return SYS_OK */
        ret;

ACQUIRE_SEMAPHORE:
        /* The self is passed by ECX, mid is passed by EDX */
        jmp  sysMonitorEnter2;
    }
}
#else
int __cdecl
sysMonitorEnter(sys_thread_t *self, sys_mon_t *mid)
{
return (SYS_NORESOURCE);
}
#endif

/*
 * Return TRUE if this thread currently owns the monitor. This can be
 * called before threads have been initialized, in which case we always
 * return TRUE.
 */
bool_t
sysMonitorEntered(sys_thread_t *self, sys_mon_t *mid)
{
    sysAssert(mid != SYS_MID_NULL);
    sysAssert(self != 0);
    sysAssert(ThreadsInitialized);

    return (mid->monitor_owner == self);
}

/*
 * Release ownership of monitor. This can be called before threads have
 * been initialized, in which case we do nothing as locked monitors are
 * not yet needed.
 *
 * The actual code is split into two functions, the assembler routine
 * handles the fast path, while the C routine handle the slow path.
 *
 * REMIND: It is EXTREMELY RISKY to change any of the code without
 * thorough understanding of the system, compiler and call convention.
 */

static int __fastcall
sysMonitorExit2(sys_thread_t *self, sys_mon_t *mid)
{
    sysAssert(mid->entry_count == 0);
    sysAssert(mid->atomic_count >= 0);
    sysAssert(mid->monitor_owner == 0);

    if (mid->semaphore == NULL) {
        initializeSemaphore(&(mid->semaphore));
        if (mid->semaphore == NULL) {
            return SYS_NORESOURCE;
        }
    }

    ReleaseSemaphore(mid->semaphore, 1, 0);

    if (profiler_on) {
        VM_CALL(monitorContendedExit)(self, mid);
    }

    return SYS_OK;
}

#ifndef _WIN64
__declspec(naked) int __cdecl
sysMonitorExit(sys_thread_t *self, sys_mon_t *mid)
{
    __asm
    {
        mov edx, dword ptr [esp+8]; /* load mid */
        mov ecx, dword ptr [esp+4]; /* load self */
        mov eax, dword ptr [edx+8]; /* load mid->monitor_owner */

        cmp eax, ecx;               /* if ( self != mid->monitor_owner ) */
        jne ERR_RET;                /* goto ERROR_RET */

        dec dword ptr [edx+12];     /* dec mid->entry_count */
        jne OK_RET;                 /* entry_count != 0 */

        mov dword ptr [edx+8], 0;   /* mid->monitor_owner = 0 */

        mov eax, dword ptr [systemIsMP];
        test eax, eax;
        jne MPM;

        dec dword ptr [edx];        /* atomic decrement mid->atomic_count */
        jge RELEASE_SEMAPHORE;      /* if ( atomic_variable < 0 ) */
        xor eax, eax;               /* return SYS_OK */
        ret;

MPM:
        lock dec dword ptr [edx];   /* atomic decrement mid->atomic_count */
        jge RELEASE_SEMAPHORE;      /* if ( atomic_variable < 0 ) */
OK_RET:
        xor eax, eax;
        ret;

ERR_RET:
        mov eax, 0FFFFFFFFH;
        ret;

RELEASE_SEMAPHORE:
        jmp sysMonitorExit2;        /* forwading call */
    }
}
#else
int __cdecl
sysMonitorExit(sys_thread_t *self, sys_mon_t *mid)
{
return (-1);
}
#endif

/*
 * Notify single thread waiting on condition variable.
 */
int
sysMonitorNotify(sys_thread_t *self, sys_mon_t *mid)
{
    sysAssert(mid != SYS_MID_NULL);

    if (mid->monitor_owner != self) {
        return SYS_ERR;
    }

    if (mid->monitor_waiter != SYS_THREAD_NULL)
    {
        sys_thread_t *thread = mid->monitor_waiter;
        mid->monitor_waiter = thread->next_waiter;

        thread->next_waiter = SYS_THREAD_NULL;
        thread->wait_monitor = SYS_MID_NULL;

        SetEvent(thread->interrupt_event);
    }

    return SYS_OK;
}

/*
 * Notify all threads waiting on condition variable.
 */
int
sysMonitorNotifyAll(sys_thread_t *self, sys_mon_t *mid)
{
    sysAssert(mid != SYS_MID_NULL);

    if (mid->monitor_owner != self) {
        return SYS_ERR;
    }

    while (mid->monitor_waiter != SYS_THREAD_NULL)
    {
        sys_thread_t *thread = mid->monitor_waiter;
        mid->monitor_waiter = thread->next_waiter;

        thread->next_waiter = SYS_THREAD_NULL;
        thread->wait_monitor = SYS_MID_NULL;

        SetEvent(thread->interrupt_event);
    }

    return SYS_OK;
}

/*
 * Atomically drop mutex and wait for notification.
 */
int
sysMonitorWait(sys_thread_t *self, sys_mon_t *mid, jlong millis)
{
    long entry_count;
    DWORD timeout;

    sysAssert(mid != SYS_MID_NULL);

    if (mid->monitor_owner != self) {
        return SYS_ERR;
    }

    if ( sysThreadIsInterrupted(self, 1) ) {
        return SYS_INTRPT;
    }

    entry_count = mid->entry_count;
    mid->entry_count = 1;

    self->wait_monitor = mid;
    self->next_waiter = 0;

    if (mid->monitor_waiter == 0) {
        mid->monitor_waiter = self;
    } else {
        sys_thread_t *thread = mid->monitor_waiter;
        while (thread->next_waiter != 0) {
            thread = thread->next_waiter;
        }
        thread->next_waiter = self;
    }

    if ( millis == SYS_TIMEOUT_INFINITY ||
        millis > (jlong)((unsigned int)0xffffffff) ) {
        timeout = INFINITE;
    } else {
        timeout = (long) millis;
    }

    mid->waiter_count++;

    sysMonitorExit(self, mid);

    self->state = CONDVAR_WAIT;

    WaitForSingleObject(self->interrupt_event, timeout);

    self->state = RUNNABLE;

    sysMonitorEnter(self, mid);

    mid->waiter_count--;

    mid->entry_count = entry_count;
    /* Reset event anyway, prevent racing the timeout */
    ResetEvent(self->interrupt_event);

    if (self->wait_monitor != SYS_MID_NULL) {
        sys_thread_t *head;

        sysAssert( self->wait_monitor == mid );
        sysAssert( mid->monitor_waiter != SYS_THREAD_NULL );

        head = mid->monitor_waiter;

        if (head == self) {
            mid->monitor_waiter = self->next_waiter;
        } else {
            while (head != SYS_THREAD_NULL) {
                if (head->next_waiter == self) {
                    head->next_waiter = self->next_waiter;
                    break;
                } else {
                    head = head->next_waiter;
                }
            }
        }

        self->next_waiter = SYS_THREAD_NULL;
        self->wait_monitor = SYS_MID_NULL;
    }

    if ( sysThreadIsInterrupted(self, 1) ) {
        return SYS_INTRPT;
    }

    return SYS_OK;
}

static int
dumpWaitingQueue(sys_thread_t *tid, sys_thread_t **waiters, int sz)
{
    int n;
    for (n = 0; tid != 0; tid = tid->next_waiter, n++, sz--) {
        if (sz > 0) {
            waiters[n] = tid;
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
    if (t->enter_monitor == winfo->mid) {
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

    info->n_condvar_waiters = dumpWaitingQueue(mid->monitor_waiter,
                                               info->condvar_waiters,
                                               info->sz_condvar_waiters);

    return SYS_OK;
}

/*
 * Return size of system-dependent monitor structure.
 */
size_t
sysMonitorSizeof(void)
{
    return sizeof(struct sys_mon);
}


/*
 *  Return true if there are any threads inside this monitor.
 */
bool_t
sysMonitorInUse(sys_mon_t *mid)
{
    return (mid->atomic_count != -1
        || mid->waiter_count != 0
        || mid->monitor_owner != SYS_THREAD_NULL
        || mid->monitor_waiter != SYS_THREAD_NULL);
}


sys_thread_t *
sysMonitorOwner(sys_mon_t *mon)
{
    return mon->monitor_owner;
}
