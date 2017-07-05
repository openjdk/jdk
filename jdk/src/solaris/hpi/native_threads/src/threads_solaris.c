/*
 * Copyright (c) 1998, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * Implementation of HPI that can not be expressed with POSIX threads.
 * Note that even if you are building with USE_PTHREADS, we have to
 * explicitly undef it here because pthread.h and thread.h can not be
 * included in the same file, and this file needs only thread.h.
 */
#undef USE_PTHREADS

#include "hpi_impl.h"
#include "monitor_md.h"
#include "threads_md.h"
#include "np.h"

#include <thread.h>
#include <sys/lwp.h>
#include <signal.h>
#include <sys/signal.h>
#include <sys/resource.h>
#include <sys/procfs.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <errno.h>

#include <sys/syscall.h>
extern int syscall(int, ...);


/*
 * Forward declarations.
 */
static int  procfd;
static void stop_lwps();
static void clear_onproc_flags();
static void restart_lwps();
static void MakeProcName(register char *procname, register pid_t pid);
static void GC_msec_sleep(int n);


/*
 * Make sure that we link against a verion of libthread that has at least
 * the bug fixes and the interface for getting the stack from threads that
 * aren't on LWPs.  Otherwise we should exit with some informative message.
 */
extern ulong_t __gettsp(thread_t);

static const char * gettspMessage =
"You must install a Solaris patch to run the native threads version of the\n"
"Java runtime.  The green threads version will work without this patch.\n"
"Please check the native threads release notes for more information.\n"
"\n"
"If you are embedding the VM in a native application, please make sure that\n"
"the native application is linked with libthread.so (-lthread).\n"
"\n"
"Exiting.\n";

static void
checkForCorrectLibthread()
{
    if (&__gettsp == 0) {
        fprintf(stderr, gettspMessage);
        exit(1);
    }
}
#ifdef __GNUC__
static void checkForCorrectLibthread() __attribute__((constructor));
#else
#pragma init(checkForCorrectLibthread)
#endif

#pragma weak __gettsp


/*
 * Suspend said thread.  Used to implement java.lang.Thread.suspend(),
 * which is deprecated.
 */
int
np_suspend(sys_thread_t *tid)
{
    return thr_suspend(tid->sys_thread);
}


/*
 * Resume a suspended thread.  Used to implement java.lang.Thread.resume(),
 * which is deprecated.
 */
int
np_continue(sys_thread_t *tid)
{
    return thr_continue(tid->sys_thread);
}

/*
 * If there is any initialization is required by the non-POSIX parts.
 */
void np_initialize_thread(sys_thread_t *tid)
{
    return;
}


/*
 * Get the stack start address, and max stack size for the current thread.
 */
int
np_stackinfo(void **addr, long *size)
{
    stack_t stkseg;

    if (thr_stksegment(&stkseg) == 0) {
        *addr = (void *)(stkseg.ss_sp);
        if (thr_main()) {
            struct rlimit r;
            getrlimit(RLIMIT_STACK, &r);
            *size = (long)r.rlim_cur;
        } else {
            *size = (long)(stkseg.ss_size);
        }
        return SYS_OK;
    } else {
        return SYS_ERR; /* thr_stksegment failed. */
    }
}

/*
 * On Solaris when doing CPU profiling, the threads are bound.
 */
void
np_profiler_init(sys_thread_t *tid)
{
    tid->lwp_id = _lwp_self();
}

int
np_profiler_suspend(sys_thread_t *tid)
{
    return _lwp_suspend(tid->lwp_id);
}

int
np_profiler_continue(sys_thread_t *tid)
{
    return _lwp_continue(tid->lwp_id);
}

bool_t
np_profiler_thread_is_running(sys_thread_t *tid)
{
    unsigned long sum = 0;
    int i;
    prstatus_t lwpstatus;
    int lwpfd;
    int res;

    lwpfd = syscall(SYS_ioctl, procfd, PIOCOPENLWP, &(tid->lwp_id));
    sysAssert(lwpfd >= 0);

 retry:
    res = syscall(SYS_ioctl, lwpfd, PIOCSTATUS, &lwpstatus);
    sysAssert(res >= 0);

    if (!(lwpstatus.pr_flags & PR_STOPPED)) {
        GC_msec_sleep(1);
        goto retry;
    }

    close(lwpfd);

#if   defined(sparc)
    sum += lwpstatus.pr_reg[R_SP];
    sum += lwpstatus.pr_reg[R_PC];

    sum += lwpstatus.pr_reg[R_G1];
    sum += lwpstatus.pr_reg[R_G2];
    sum += lwpstatus.pr_reg[R_G3];
    sum += lwpstatus.pr_reg[R_G4];

    sum += lwpstatus.pr_reg[R_O0];
    sum += lwpstatus.pr_reg[R_O1];
    sum += lwpstatus.pr_reg[R_O2];
    sum += lwpstatus.pr_reg[R_O3];
    sum += lwpstatus.pr_reg[R_O4];
    sum += lwpstatus.pr_reg[R_O5];

    sum += lwpstatus.pr_reg[R_I0];
    sum += lwpstatus.pr_reg[R_I1];
    sum += lwpstatus.pr_reg[R_I2];
    sum += lwpstatus.pr_reg[R_I3];
    sum += lwpstatus.pr_reg[R_I4];
    sum += lwpstatus.pr_reg[R_I5];
    sum += lwpstatus.pr_reg[R_I6];
    sum += lwpstatus.pr_reg[R_I7];

    sum += lwpstatus.pr_reg[R_L0];
    sum += lwpstatus.pr_reg[R_L1];
    sum += lwpstatus.pr_reg[R_L2];
    sum += lwpstatus.pr_reg[R_L3];
    sum += lwpstatus.pr_reg[R_L4];
    sum += lwpstatus.pr_reg[R_L5];
    sum += lwpstatus.pr_reg[R_L6];
    sum += lwpstatus.pr_reg[R_L7];
#elif defined(amd64)
    sum += lwpstatus.pr_reg[REG_RIP];
    sum += lwpstatus.pr_reg[REG_RSP];

    sum += lwpstatus.pr_reg[REG_RAX];
    sum += lwpstatus.pr_reg[REG_RCX];
    sum += lwpstatus.pr_reg[REG_RDX];
    sum += lwpstatus.pr_reg[REG_RBX];
    sum += lwpstatus.pr_reg[REG_RBP];
    sum += lwpstatus.pr_reg[REG_RSI];
    sum += lwpstatus.pr_reg[REG_RDI];

    sum += lwpstatus.pr_reg[REG_R8];
    sum += lwpstatus.pr_reg[REG_R9];
    sum += lwpstatus.pr_reg[REG_R10];
    sum += lwpstatus.pr_reg[REG_R11];
    sum += lwpstatus.pr_reg[REG_R12];
    sum += lwpstatus.pr_reg[REG_R13];
    sum += lwpstatus.pr_reg[REG_R14];
    sum += lwpstatus.pr_reg[REG_R15];
#elif defined(i386)
    sum += lwpstatus.pr_reg[EIP];
    sum += lwpstatus.pr_reg[UESP];

    sum += lwpstatus.pr_reg[EAX];
    sum += lwpstatus.pr_reg[ECX];
    sum += lwpstatus.pr_reg[EDX];
    sum += lwpstatus.pr_reg[EBX];
    sum += lwpstatus.pr_reg[EBP];
    sum += lwpstatus.pr_reg[ESI];
    sum += lwpstatus.pr_reg[EDI];
#endif

    if (tid->last_sum == sum) {
        return FALSE;
    }
    tid->last_sum = sum;

    return TRUE;
}


/*
 * If building for Solaris native threads, open up the /proc file
 * descriptor to be used when doing GC. The open is done at JVM start-up so
 * as to reserve this fd, to prevent GC stall due to exhausted fds. This fd
 * will never be closed, and will alwyas be present.
 */
int
np_initialize()
{
    char procname[32];
    MakeProcName(procname, getpid());
    if ((procfd = open(procname, O_RDONLY, 0)) < 0) {
        VM_CALL(jio_fprintf)(stderr, "Cannot open %s for GC", procname);
        return SYS_ERR;
    }
    return SYS_OK;
}

static void
MakeProcName(register char *procname, register pid_t pid)
{
    register char * s;

    (void) strcpy(procname, "/proc/00000");
    s = procname + strlen(procname);
    while (pid) {
        *--s = pid%10 + '0';
        pid /= 10;
    }
}

/*
 * Suspend all other threads, and record their contexts (register
 * set or stack pointer) into the sys_thread structure, so that a
 * garbage collect can be run.
 */
int
np_single(void)
{
    int ret;

    sysAssert(SYS_QUEUE_LOCKED(sysThreadSelf()));

    stop_lwps();
    ret = SYS_OK;
    return ret;
}

/*
 * Continue threads suspended earlier.  But clear their context
 * recorded in sys_thread structure first.
 */
void
np_multi(void)
{
    sysAssert(SYS_QUEUE_LOCKED(sysThreadSelf()));
    clear_onproc_flags();
    restart_lwps();
}

/* /proc solution to stop and restrt lwps */
/* make sure gc is run as a bound thread */
/* make sure signals are turned off for gc thread */
/* what about new lwps getting created in the meantime? */

#define MAX_LWPS 1024

static prstatus_t Mystatus;
static id_t lwpid_list_buf[MAX_LWPS];
static id_t oldlwpid_list_buf[MAX_LWPS];
static sys_thread_t *onproct_list_buf[MAX_LWPS];
static id_t *lwpid_list = lwpid_list_buf;
static id_t *oldlwpid_list = oldlwpid_list_buf;
static sys_thread_t **onproct_list = onproct_list_buf;
static int lwpid_list_len;
static int oldlwpid_list_len;
static int onproct_ix = 0;
static int gcprio;
static sigset_t gcmask;

static void
clear_onproc_flags()
{
    int i;

    for (i = 0; i < onproct_ix; i++) {
        ((sys_thread_t *)(onproct_list[i]))->onproc = FALSE;
    }
    onproct_ix = 0;
}


/* Sleep for n milliseconds, n < 1000   */
static void
GC_msec_sleep(int n)
{
    struct timespec ts;

    ts.tv_sec = 0;
    ts.tv_nsec = 1000000*n;
    if (syscall(SYS_nanosleep, &ts, 0) < 0) {
        VM_CALL(jio_fprintf)(stderr, "%d\n", errno);
    }
}

/*
 * Assumes stacks grow down from high to low memory. True on sparc and Intel.
 */
#define VALID_SP(sp, bottom, top) \
       (((uintptr_t)(sp)) < ((uintptr_t)(bottom)) && ((uintptr_t)(sp)) > ((uintptr_t)(top)))

static void
record_lwp_regs(prstatus_t lwpstatus)
{
    sys_thread_t *tid;
    int i;
#if   defined(sparc)
    register uintptr_t sp = lwpstatus.pr_reg[R_SP];
#elif defined(amd64)
    register uintptr_t sp = lwpstatus.pr_reg[REG_RSP];
#elif defined(i386)
    register uintptr_t sp = lwpstatus.pr_reg[UESP];
#endif

    tid = ThreadQueue;
    for (i = 0; i < ActiveThreadCount && tid != 0; i++) {
        if (VALID_SP(sp, tid->stack_bottom, tid->stack_top)) {
            long *regs = tid->regs;
            tid->sp = sp;
            /*
             * The code below relies on N_TRACED_REGS being set
             * correctly for each platform.  If you change the
             * number of registers being watched, you should update
             * the define for N_TRACED_REGS
             */
#if   defined(sparc)
            regs[0] = lwpstatus.pr_reg[R_G1];
            regs[1] = lwpstatus.pr_reg[R_G2];
            regs[2] = lwpstatus.pr_reg[R_G3];
            regs[3] = lwpstatus.pr_reg[R_G4];

            regs[4] = lwpstatus.pr_reg[R_O0];
            regs[5] = lwpstatus.pr_reg[R_O1];
            regs[6] = lwpstatus.pr_reg[R_O2];
            regs[7] = lwpstatus.pr_reg[R_O3];
            regs[8] = lwpstatus.pr_reg[R_O4];
            regs[9] = lwpstatus.pr_reg[R_O5];
            regs[10] = lwpstatus.pr_reg[R_O6];
            regs[11] = lwpstatus.pr_reg[R_O7];
#elif defined(amd64)
            regs[0] = lwpstatus.pr_reg[REG_RAX];
            regs[1] = lwpstatus.pr_reg[REG_RCX];
            regs[2] = lwpstatus.pr_reg[REG_RDX];
            regs[3] = lwpstatus.pr_reg[REG_RBX];
            regs[4] = lwpstatus.pr_reg[REG_RBP];
            regs[5] = lwpstatus.pr_reg[REG_RSI];
            regs[6] = lwpstatus.pr_reg[REG_RDI];
            regs[7] = lwpstatus.pr_reg[REG_R8];
            regs[8] = lwpstatus.pr_reg[REG_R9];
            regs[9] = lwpstatus.pr_reg[REG_R10];
            regs[10]= lwpstatus.pr_reg[REG_R11];
            regs[11]= lwpstatus.pr_reg[REG_R12];
            regs[12]= lwpstatus.pr_reg[REG_R13];
            regs[13]= lwpstatus.pr_reg[REG_R14];
            regs[14]= lwpstatus.pr_reg[REG_R15];
#elif defined(i386)
            regs[0] = lwpstatus.pr_reg[EAX];
            regs[1] = lwpstatus.pr_reg[ECX];
            regs[2] = lwpstatus.pr_reg[EDX];
            regs[3] = lwpstatus.pr_reg[EBX];
            regs[4] = lwpstatus.pr_reg[EBP];
            regs[5] = lwpstatus.pr_reg[ESI];
            regs[6] = lwpstatus.pr_reg[EDI];
#endif

            if (tid->onproc != TRUE) {
                tid->onproc = TRUE;
                onproct_list[onproct_ix++] = tid;
            }
            break;
        }
        tid = tid->next;
    }
}

static void
record_thread_regs()
{
    sys_thread_t *tid;
    int i;

    tid = ThreadQueue;
    for (i = 0; i < ActiveThreadCount && tid != 0; i++) {
        if (tid->onproc != TRUE) {
            int i;

            if (tid->sys_thread != 0) {
                /* if thread has already been initialized */
                tid->sp = __gettsp(tid->sys_thread);
            } else {
                /*
                 * thread is still in the process of being initalized.
                 * So GC should not care about this thread. Just
                 * set its sp to 0, and this will force GC to ignore it.
                 */
                tid->sp = 0;
            }

            /*
             * Clear out the registers since they are no longer live
             * and we don't want to garbage collector to think they are.
             */

            for (i = 0; i < N_TRACED_REGS; i++)
                tid->regs[i] = 0;
        }
        tid = tid->next;
    }
}

static void
wait_stopped_lwps(void)
{
    int i, lwpfd;
    prstatus_t lwpstatus;

    for (i = 0; i < (int) Mystatus.pr_nlwp; i++) {
        /* if its  not me */
        if (lwpid_list[i] != _lwp_self()) {

            /* open the lwp and check the status */
            if ((lwpfd = syscall(SYS_ioctl, procfd, PIOCOPENLWP,
                &lwpid_list[i])) < 0) {
#ifdef MY_DEBUG
                VM_CALL(jio_fprintf)(stderr, "lwpid %d was not found in process\n",
                            lwpid_list[i]);
#endif
                continue;
            }
            memset(&lwpstatus, 0, sizeof(lwpstatus));
            while (1) {
                if (syscall(SYS_ioctl,lwpfd, PIOCSTATUS, &lwpstatus)<0) {
                    sysAssert(0);
#ifdef MY_DEBUG
                    VM_CALL(jio_fprintf)(stderr, "PIOCSTATUS failed for lwp %d",
                                lwpid_list[i]);
#endif
                    break;
                }
                if (lwpstatus.pr_flags & PR_STOPPED) {
                    record_lwp_regs(lwpstatus);
                    break;
                }
                GC_msec_sleep(1);
            }

            close (lwpfd);
        } /* end of if-me */
    } /* end of for */
}

static void
suspend_lwps()
{
    int i;
    /* pioopen all the lwps and stop them - except the one I am running on */
    for (i = 0; i < (int) Mystatus.pr_nlwp; i++) {

        /* open and stop the lwp if its not me */
        if (lwpid_list[i] != _lwp_self()) {

            /* PIOCSTOP doesn't work without a writable         */
            /* descriptor.  And that makes the process          */
            /* undebuggable.                                    */
            if (_lwp_suspend(lwpid_list[i]) < 0) {
                        /* Could happen if the lwp exited */
                lwpid_list[i] = _lwp_self();
                continue;
            }
        }
    }
}

static void
print_lwps()
{
#ifdef MY_DEBUG
    /* print all the lwps in the process */
    VM_CALL(jio_fprintf)(stdout, "lwpids ");
    for (i = 0; i < (int) Mystatus.pr_nlwp; i++) {
        if (i == 0) {
            VM_CALL(jio_fprintf)(stdout, "%d", lwpid_list[0]);
        } else if (i != Mystatus.pr_nlwp - 1) {
            VM_CALL(jio_fprintf)(stdout, ", %d", lwpid_list[i]);
        } else {
            VM_CALL(jio_fprintf)(stdout, " and %d", lwpid_list[i]);
        }
    }
#endif
}

/* routine to iteratively stop all lwps */
static void
stop_lwps()
{
    int i;
    sigset_t set;
    boolean_t changed;

    /* mask all signals */
    (void) sigfillset(&set);
    syscall(SYS_sigprocmask, SIG_SETMASK, &set, &gcmask);

    /* run at highest prio so I cannot be preempted */
    thr_getprio(thr_self(), &gcprio);
    thr_setprio(thr_self(), 2147483647);  /* #define INT_MAX 2147483647 */

    oldlwpid_list_len = 0;

    while(1) {
        changed = B_FALSE;

        /* Get the # of lwps in the process */
        memset(&Mystatus, 0, sizeof(Mystatus));
        syscall(SYS_ioctl, procfd, PIOCSTATUS, &Mystatus);

#ifdef MY_DEBUG
        VM_CALL(jio_fprintf)(stdout, "Number of lwps in the process is %d\n",
                    Mystatus.pr_nlwp);
        VM_CALL(jio_fprintf)(stdout, "My lwp id is %d\n", _lwp_self());
#endif
        lwpid_list_len = Mystatus.pr_nlwp;
        if (syscall(SYS_ioctl, procfd, PIOCLWPIDS, lwpid_list) == -1) {
#ifdef MY_DEBUG
            VM_CALL(jio_fprintf)(stderr, "Can't read proc's lwpid list");
#endif
            return;
        }

        print_lwps();

        /* suspend all the lwps */
        suspend_lwps();

        /* make sure all the lwps have actually stopped */
        wait_stopped_lwps();

        /* make sure the list has not changed while you were not looking
           else start all over again */
        if (lwpid_list_len != oldlwpid_list_len) changed = B_TRUE;
        else  {
            for (i=0; i<lwpid_list_len; ++i) {
                if (lwpid_list[i] != oldlwpid_list[i]) {
                    changed = B_TRUE; break;
                }
            }
        }
        if (!changed) break;

        {
            id_t *tmplwpid_list = oldlwpid_list;
            oldlwpid_list = lwpid_list; oldlwpid_list_len = lwpid_list_len;
            lwpid_list = 0; lwpid_list_len = 0;
            lwpid_list = tmplwpid_list;
        }
    }

    /* record regs for threads that were not on LWPs */
    record_thread_regs();

    return;
}


/* Restart all lwps in process.  */
static void
restart_lwps()
{
    int i;

    for (i = 0; i < Mystatus.pr_nlwp; i++) {
        if (lwpid_list[i] == _lwp_self()) continue;
        if (_lwp_continue(lwpid_list[i]) < 0) {
#ifdef MY_DEBUG
            VM_CALL(jio_fprintf)(stderr, "Failed to restart lwp %d\n",lwpid_list[i]);
#endif
        }
    }

    /* restore the old priority of the thread */
    thr_setprio(thr_self(), gcprio);
    /* restore the oldmask */
    syscall(SYS_sigprocmask, SIG_SETMASK, &gcmask, NULL);

    print_lwps();
}
