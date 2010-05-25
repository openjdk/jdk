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
 * Monitor interface    10/25/94
 *
 * Private data structures and interfaces used in the monitor code.
 * This file is used to share declarations and such between the different
 * files implementing monitors.  It does not contain exported API.
 */

#ifndef _JAVASOFT_SOLARIS_MONITOR_MD_H_
#define _JAVASOFT_SOLARIS_MONITOR_MD_H_

#include <mutex_md.h>
#include <condvar_md.h>
#include <threads_md.h>

/*
 * Type definitions.
 */

typedef struct monitor_waiter monitor_waiter_t;
typedef struct monitor_wait_queue monitor_wait_queue_t;

/* Element of the MonitorWaitQ - representing thread doing a monitor wait */
/*
 * The only reason we do the queueing is for sysMonitorDumpInfo.
 * The counting, though, is used to avoid extraneous calls to
 * condvarBroadcast and condvarSignal, for instance.
 */
struct monitor_waiter {
    monitor_waiter_t   *next;
    monitor_waiter_t  **prev;
    sys_thread_t       *waiting_thread;
};

struct monitor_wait_queue {
    monitor_waiter_t   *head;           /* linked list of waiting threads */
    short               count;          /* number of waiters on the list */
};

#define ANY_WAITING(mwq) ((mwq).count > 0)
#define INIT_MONITOR_WAIT_QUEUE(mwq) { (mwq).head = NULL; (mwq).count = 0; }

/* The system-level monitor data structure */
struct sys_mon {
    mutex_t             mutex;          /* The monitor's mutex */
    condvar_t           cv_monitor;     /* Notify those doing monitorWait on
                                           the monitor */
    /*
     * Threads waiting on either of the above condvars put themselves
     * on one of these lists.
     */
    monitor_wait_queue_t mwait_queue;   /* Head of MonitorWaitQ */

    /* Thread currently executing in this monitor */
    sys_thread_t        *monitor_owner;
    long                entry_count;    /* Recursion depth */
    int                 contention_count;
};

void initializeContentionCountMutex();

typedef enum {
        ASYNC_REGISTER,
        ASYNC_UNREGISTER
} async_action_t;

/*
 * Macros
 */

#define SYS_MID_NULL ((sys_mon_t *) 0)

typedef enum {
        SYS_ASYNC_MON_ALARM = 1,
        SYS_ASYNC_MON_IO,
        SYS_ASYNC_MON_EVENT,
        SYS_ASYNC_MON_CHILD,
        SYS_ASYNC_MON_MAX
} async_mon_key_t;

#define SYS_ASYNC_MON_INPUT SYS_ASYNC_MON_IO
#define SYS_ASYNC_MON_OUTPUT SYS_ASYNC_MON_IO

sys_mon_t *asyncMon(async_mon_key_t);

#endif /* !_JAVASOFT_SOLARIS_MONITOR_MD_H_ */
