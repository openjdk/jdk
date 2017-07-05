/*
 * Copyright (c) 1994, 1999, Oracle and/or its affiliates. All rights reserved.
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
 * Win32 implementation of Java threads
 */

#ifndef _JAVASOFT_WIN32_THREADS_MD_H_
#define _JAVASOFT_WIN32_THREADS_MD_H_

#include <windows.h>

#define N_TRACED_REGS 7

#define SYS_THREAD_NULL         ((sys_thread_t *) 0)

/*
 * Machine dependent info in a sys_thread_t: Keep these values in
 * sync with the string array used by sysThreadDumpInfo() in threads_md.c!
 */
typedef enum {
    FIRST_THREAD_STATE,
    RUNNABLE = FIRST_THREAD_STATE,
    SUSPENDED,
    MONITOR_WAIT,
    CONDVAR_WAIT,
    MONITOR_SUSPENDED,
    NUM_THREAD_STATES
} thread_state_t;

struct sys_mon;

/*
 * Machine dependent thread data structure
 */
typedef struct sys_thread {
    HANDLE handle;                  /* Win32 thread handle */
    unsigned long id;               /* Win32 thread id */
    long regs[N_TRACED_REGS];       /* Registers */
    thread_state_t state;           /* Current thread state */
    bool_t system_thread;           /* TRUE if this is a system thread */
    bool_t interrupted;             /* Shadow thread interruption */
    short  suspend_flags;
    HANDLE interrupt_event;         /* Event signaled on thread interrupt */
    struct sys_mon *wait_monitor;   /* Monitor the thread is waiting for */
    struct sys_thread *next_waiter; /* Next thread in the waiting queue */
    struct sys_mon *enter_monitor;  /* Monitor thread is waiting to enter */
    void (*start_proc)(void *);    /* Thread start routine address */
    void *start_parm;               /* Thread start routine parameter */
    struct sys_thread *next;        /* Next thread in active thread queue */
    void *stack_ptr;                /* Pointer into the stack segment */
    unsigned int last_sum;
    PNT_TIB nt_tib;                 /* Pointer to NT thread-local block */
} sys_thread_t;

#define MONITOR_WAIT_SUSPENDED 0x0001
#define CONDVAR_WAIT_SUSPENDED 0x0002

extern bool_t ThreadsInitialized;

extern sys_mon_t *_sys_queue_lock;

#define SYS_QUEUE_LOCK(self)    sysMonitorEnter(self, _sys_queue_lock)
#define SYS_QUEUE_LOCKED(self)  sysMonitorEntered(self, _sys_queue_lock)
#define SYS_QUEUE_UNLOCK(self)  sysMonitorExit(self, _sys_queue_lock)
#define SYS_QUEUE_NOTIFYALL(self)  sysMonitorNotifyAll(self, _sys_queue_lock)
#define SYS_QUEUE_WAIT(self) sysMonitorWait(self, _sys_queue_lock, \
                                        SYS_TIMEOUT_INFINITY)

#endif /* !_JAVASOFT_WIN32_THREADS_MD_H_ */
