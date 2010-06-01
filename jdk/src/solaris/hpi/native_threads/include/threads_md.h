/*
 * Copyright (c) 1994, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * Implementation of the Java threads HPI on top of Solaris threads
 */

#ifndef _JAVASOFT_SOLARIS_THREADS_MD_H_
#define _JAVASOFT_SOLARIS_THREADS_MD_H_

#include "porting.h"

#ifdef sparc
#define N_TRACED_REGS 12
#elif i386
#define N_TRACED_REGS 7
#elif amd64
#define N_TRACED_REGS 15
#elif ppc
#define N_TRACED_REGS 1
#elif m68k
#define N_TRACED_REGS 8
#elif ia64
/* [RGV] I don't think this is referenced in the linux code */
#define N_TRACED_REGS 32
#else
// TODO: delete this file - threads_md.[ch] is obsolete. struct sys_thread is
// never used. Define a value just to keep compiler happy.
#define N_TRACED_REGS 32
#endif

/* Turn on if we want all java threads to be bound tolwps */
/* #define BOUND_THREADS */
/* Use /proc soln to stop lwps in place of siglwp soln */
#define PROCLWP

/*
 * Thread C stack overflow check
#define sysThreadCheckStack(redzone, var)                               \
    ((var = sysThreadSelf()) &&                                         \
      (unsigned int)((char *)&(var) - (char *)(var)->stack_base)\
                < (redzone))
*/
/*
 * Forward definition of machine dependent monitor struct
 */
struct sys_mon;

/*
 * The following thread states are really hints since there is no
 * interface to get at a native thread's true state. The states
 * are maintained by updating the states where ever possible
 * such as a transition to CONDVAR_WAIT occurs in the definition of
 * the routine condvarWait().
 * This state maintenance should disappear once we have a threads interface
 * to obtain a thread's state.
 */
typedef enum {
    FIRST_THREAD_STATE,
    RUNNABLE = FIRST_THREAD_STATE,
    SUSPENDED,
    CONDVAR_WAIT,
    NUM_THREAD_STATES
} thread_state_t;

#if defined( USE_PTHREADS) && !defined(__linux__)
/*
 * Mechanism for starting a new thread suspended.
 */
typedef enum {
    NEW_THREAD_MUST_REQUEST_SUSPEND,
    NEW_THREAD_REQUESTED_SUSPEND,
    NEW_THREAD_SUSPENDED
} new_thr_state_t;

typedef struct {
    pthread_mutex_t     m;
    pthread_cond_t      c;
    new_thr_state_t     state;
} new_thr_cond_t;
#endif /* USE_PTHREADS */

/*
 * Machine dependent info in a sys_thread_t
 */
struct sys_thread {
    /*
     * Fields below this point may change on a per-architecture basis
     * depending on how much work is needed to present the sysThread
     * model on any given thread implementation.
     */
    mutex_t mutex;              /* per thread lock to protect thread fields */
    thread_t sys_thread;                /* The native thread id */
    struct sys_thread *next;            /* Pointer to next thread in the */
                                        /* queue of all threads. */
    thread_state_t state;

    /* Thread status flags */
    unsigned int primordial_thread:1;
    unsigned int system_thread:1;
    unsigned int cpending_suspend:1;
#ifdef __linux__
    unsigned int pending_interrupt:1;
#endif
    unsigned int interrupted:1;
    unsigned int onproc:1; /* set if thread is on an LWP */
    unsigned int :0;

#ifdef BOUND_THREADS
    lwpid_t lwpid;
#endif

#ifdef __linux__
    void *sp;
#else
    unsigned long  sp;   /* sp at time of last (native) thread switch */
#endif
    void * stack_bottom; /* The real bottom (high address) of stack */
    void * stack_top;    /* should be equal to stack_bottom - stack_size */
    long   stack_size;   /* The stack size for a native thread */

    long regs[N_TRACED_REGS]; /* stores registers as GC roots. */

    /* Monitor specific.

       Every monitor keeps track of the number of times it is
       entered.  When that count goes to 0, the monitor can be
       freed up.  But each thread has its own entry count on a
       particular monitor, because multiple threads can be using a
       single monitor (as one does a wait, another enters, etc.).
       Each thread can only be waiting in exactly one monitor.
       That monitor waited on is saved in mon_wait, and the value
       of the monitor's entry_count when the wait was performed is
       saved in monitor_entry_count.  That is restored into the
       monitor when this waiting thread is notified. */

    long monitor_entry_count;           /* For recursive monitor entry */
    struct sys_mon *mon_wait;           /* CONDVAR_WAIT'ing */

    struct sys_mon *mon_enter;          /* blocked waiting to enter */

    void (*start_proc)(void *);
    void *start_parm;
    int lwp_id;
    long last_sum;

    struct sys_thread *prevBlocked;     /* Used by nonblocking close semantics */
    struct sys_thread *nextBlocked;
#ifdef USE_PTHREADS
    int suspend_count;
#ifdef __linux__
    sem_t sem_suspended;
    sem_t sem_ready_to_suspend;
    sem_t sem_selfsuspend;
    int selfsuspended;
#endif
#ifdef USE_MUTEX_HANDSHAKE
    new_thr_cond_t ntcond;
#else
    sem_t sem;
#endif /* USE_MUTEX_HANDSHAKE */
#endif /* USE_PTHREADS */
};

#define SYS_THREAD_NULL         ((sys_thread_t *) 0)

/*
 * following macro copied from sys/signal.h since inside #ifdef _KERNEL there.
 */
#ifndef sigmask
#define sigmask(n)      ((unsigned int)1 << (((n) - 1) & (32 - 1)))
#endif

#ifdef __linux__
extern thread_key_t intrJmpbufkey;
#else
extern thread_key_t sigusr1Jmpbufkey;
extern sigset_t sigusr1Mask;
#endif

extern sys_mon_t *_sys_queue_lock;

#define SYS_QUEUE_LOCK(self)    sysMonitorEnter(self, _sys_queue_lock)
#define SYS_QUEUE_LOCKED(self)  sysMonitorEntered(self, _sys_queue_lock)
#define SYS_QUEUE_UNLOCK(self)  sysMonitorExit(self, _sys_queue_lock)
#define SYS_QUEUE_NOTIFYALL(self)  sysMonitorNotifyAll(self, _sys_queue_lock)
#define SYS_QUEUE_WAIT(self) sysMonitorWait(self, _sys_queue_lock, \
                                        SYS_TIMEOUT_INFINITY)

extern void setFPMode(void);

extern sys_thread_t *ThreadQueue;

extern int ActiveThreadCount;           /* All threads */

#endif /* !_JAVASOFT_SOLARIS_THREADS_MD_H_ */
