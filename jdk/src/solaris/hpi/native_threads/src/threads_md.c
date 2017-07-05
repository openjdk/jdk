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
 * Implementation of Java threads HPI on top of native Solaris threads
 *
 * [Sheng 1/18/97] Do not include any JVM-specific header file (such
 * as interpreter.h) here! This file implements the thread-related
 * APIs sys_api.h.
 */

#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <assert.h>
#include <setjmp.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/signal.h>
#include <sys/resource.h>

#include "hpi_impl.h"

#include "threads_md.h"
#include "monitor_md.h"

#include "np.h"

extern int InitializeIO(rlim_t limit);

#if defined(__solaris__) && !defined(SA_SIGINFO)
#error That can NOT possibly be right.
#endif

#ifdef SA_SIGINFO
static void sigusr1Handler(int sig, siginfo_t *info, void *uc);
#else
static void sigusr1Handler(int sig);
#endif /* SA_SIGINFO */

static void removeFromActiveQ(sys_thread_t *p);
static void clear_onproc_flags(void);

sys_thread_t *ThreadQueue;
int ActiveThreadCount = 0;              /* All threads */
sys_mon_t *_sys_queue_lock;

/* This is explained in linker_md.c. */
#ifdef __GLIBC__
#define NEED_DL_LOCK
#endif /* __GLIBC__ */

#ifdef NEED_DL_LOCK
extern sys_mon_t _dl_lock;
#endif /* NEED_DL_LOCK */

/*
 * threads_initialized simplifies the check that has to be done in
 * sysThreadCheckStack().  Otherwise, before sysThreadInit() is called
 * on the primordial thread, we need to handle there being no current
 * thread at all, and there being one with a 0 stack_base.
 */
static int threads_initialized = 0;

/* The tid_key is a global key to *native* thread-specific data.  That is,
 * each native thread has a back pointer to the Java TID associated with it.
 */
static thread_key_t tid_key = (thread_key_t) -1;

/*
 * The sigusr1Jmpbufkey is used to get at the jmp buffer to longjmp to in a
 * SIGUSR1 handler - used to implement stop(). The jmp buffer
 * should have been allocated off the thread's stack.
 */
#ifdef __linux__
thread_key_t intrJmpbufkey;
static sigset_t squm = {{sigmask(SIGUSR1), 0, 0, 0}};
#else
thread_key_t sigusr1Jmpbufkey;
sigset_t sigusr1Mask = {{sigmask(SIGUSR1), 0, 0, 0}};
#endif

/*
 * Thread C stack overflow check
 *
 * sysThreadCheckStack() being a function call is unfortunate, as it
 * takes stack space to do, but that is weakly accounted for by the
 * previous stack redzone.  In general, where we can't predict stack
 * use by C, thread stack overflow checking doesn't really work.
 */

#define STACK_REDZONE 4096

#ifdef __linux__
int jdk_waitpid(pid_t pid, int* status, int options);
int fork1(void);
int jdk_sem_init(sem_t*, int, unsigned int);
int jdk_sem_post(sem_t*);
int jdk_sem_wait(sem_t*);
int jdk_pthread_sigmask(int, const sigset_t*, sigset_t*);
pid_t waitpid(pid_t, int*, int);

int jdk_waitpid(pid_t pid, int* status, int options) {
    return waitpid(pid, status, options);
}

int fork1() {
    return fork();
}

int
jdk_sem_init(sem_t *sem, int pshared, unsigned int value) {
    return sem_init(sem, pshared, value);
}

int
jdk_sem_post(sem_t *sem) {
    return sem_post(sem);
}

int
jdk_sem_wait(sem_t *sem) {
    return sem_wait(sem);
}

int
jdk_pthread_sigmask(int how , const sigset_t* newmask, sigset_t* oldmask) {
    return pthread_sigmask(how , newmask, oldmask);
}

#endif

/* REMIND: port _CurrentThread changes to make process
   of getting the tid more efficient */

int
sysThreadCheckStack()
{
    sys_thread_t *tid = sysThreadSelf();

    /* Stacks grow toward lower addresses on Solaris... */
    if (!threads_initialized ||
        (char *)(tid)->stack_bottom - (char *)&(tid) + STACK_REDZONE <
            tid->stack_size) {
        return 1;
    } else {
        return 0;
    }
}

#ifndef __linux__
static sigset_t squm = {{sigmask(SIGUSR1), 0, 0, 0}};
#endif


/*
 * Allocate and initialize the sys_thread_t structure for an arbitary
 * native thread.
 */
int
sysThreadAlloc(sys_thread_t **tidP)
{
    int err;
    sys_thread_t *tid = allocThreadBlock();
    if (tid == NULL) {
        return SYS_NOMEM;
    }
#ifdef __linux__
    memset((char *)tid, 0, sizeof(sys_thread_t));
#endif

    if (profiler_on) {
        np_profiler_init(tid);
    }

    if (np_stackinfo(&tid->stack_bottom, &tid->stack_size) == SYS_ERR) {
        return SYS_ERR;
    }
#ifdef __linux__
    tid->stack_top = tid->stack_bottom - tid->stack_size;
#else
    tid->stack_top = (void *)((char *)(tid->stack_bottom) - tid->stack_size);
#endif

    tid->primordial_thread = 0;
#ifdef __linux__
    tid->interrupted = tid->pending_interrupt = FALSE;
#else
    tid->interrupted = FALSE;
#endif
    tid->onproc = FALSE;
    tid->sys_thread = thr_self();
#ifdef __linux__
    /*
     * Disable cancellation.  The default cancel type is
     * PTHREAD_CANCEL_DEFERRED, so if we set the cancel state to
     * PTHREAD_CANCEL_ENABLE again, we'll get deferred cancellation.
     */
     pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
#endif
    np_initialize_thread(tid);

    /*
     * For the Invocation API:
     * We update the thread-specific storage before locking the
     * queue because sysMonitorEnter will access sysThreadSelf.
     */
    err = thr_setspecific(tid_key, tid);
                                /* sys_thread_t * back pointer from native */
#ifdef __linux__
    thr_setspecific(intrJmpbufkey, NULL); /* paranoid */
#endif
    assert (err == 0);

    if (threads_initialized)
        SYS_QUEUE_LOCK(sysThreadSelf());
    ActiveThreadCount++;        /* Count the thread */
    tid->next = ThreadQueue;    /* Chain all threads */
    ThreadQueue = tid;
    if (threads_initialized)
        SYS_QUEUE_UNLOCK(sysThreadSelf());
    else
        threads_initialized = TRUE;

    /*
     * Ensure that SIGUSR1 is masked for interruptable IO
     * Signal mask inheritance ensures all child threads are masked too.
     */
#ifndef __linux__
    thr_sigsetmask(SIG_BLOCK, &squm, NULL);
#endif

    setFPMode();

    *tidP = tid;
    return SYS_OK;
}

/*
 * threadBootstrapMD() bootstraps the UNIX process running from main()
 * into a first primordial thread.  This thread is distinguishable because
 * it uniquely has the primordial_thread flag on in its private data.
 * However, so that we have to special-case it as little as possible, we
 * set it up to look as closely as possible to a thread that we created.
 * One difference is that its stack is *not* known to us.
 */
int
threadBootstrapMD(sys_thread_t **tidP, sys_mon_t **lockP, int nb)
{
    /* We are running out of file descriptors in fonts.  As a temporary
     * fix, bump up the number of open file descriptors to OPEN_MAX.
     */
    struct rlimit nbr_files;

    getrlimit(RLIMIT_NOFILE, &nbr_files);
    nbr_files.rlim_cur = nbr_files.rlim_max;
    setrlimit(RLIMIT_NOFILE, &nbr_files);

    /*
     * Use the above setting for initialize (closable) IO package.
     */
    if (InitializeIO(nbr_files.rlim_cur) != SYS_OK) {
        return SYS_ERR;
    }

    /* Create a thread-private key for a pointer back to the sys_thread_t *.
     * Note that we don't need to worry about the destructor, as that's taken
     * care of elsewhere.
     */
    thr_keycreate(&tid_key, NULL);

#ifdef __linux__
    thr_keycreate(&intrJmpbufkey, NULL);
#else
    thr_keycreate(&sigusr1Jmpbufkey, NULL);
#endif

#ifndef NO_INTERRUPTIBLE_IO
    {
        /* initialize SIGUSR1 handler for interruptable IO */
        struct sigaction sigAct;

#ifdef SA_SIGINFO
        sigAct.sa_handler = NULL;
        sigAct.sa_sigaction = sigusr1Handler;
#else
        sigAct.sa_handler = sigusr1Handler;
#endif /* SA_SIGINFO */
        memset((char *)&(sigAct.sa_mask), 0, sizeof (sigset_t));
        /* we do not want the restart flag for SIGUSR1 */
        sigAct.sa_flags = 0;
        sigaction(SIGUSR1, &sigAct, (struct sigaction *)0);
    }
#endif /* NO_INTERRUPTIBLE_IO */

    nReservedBytes = (nb + 7) & (~7);
    if (sysThreadAlloc(tidP) < 0) {
        return SYS_NOMEM;
    }

    /* profiler_on may have not been setup yet. */
    np_profiler_init(*tidP);

#ifdef NEED_DL_LOCK
    VM_CALL(monitorRegister)(&_dl_lock, "Dynamic loading lock");
#endif /* NEED_DL_LOCK */

    /* Initialize the queue lock monitor */
    _sys_queue_lock = (sys_mon_t *)sysMalloc(sysMonitorSizeof());
    if (_sys_queue_lock == NULL) {
        return SYS_ERR;
    }
    VM_CALL(monitorRegister)(_sys_queue_lock, "Thread queue lock");
    *lockP = _sys_queue_lock;

    (*tidP)->primordial_thread = 1;

    if (np_initialize() == SYS_ERR) {
        return SYS_ERR;
    }

    return SYS_OK;
}

/*
 * Access to the thread stack pointer of an arbitrary thread (for GC).
 * This information should be legitimately available in Solaris 2.5.
 */
void *
sysThreadStackPointer(sys_thread_t * tid)
{
    char *thread_info;

    if (tid == sysThreadSelf()) {
        /*
         * doing this assigment gets around a warning about returning
         * the address of a local variable
         */
        void *aStackAddress = &thread_info;
        return aStackAddress;
    } else {
        return (void *) tid->sp;
    }
}

/*
 * Get the end of stack (if you step beyond (above or below depending
 * on your architecture) you can die.  We refer to the logical top of
 * stack.
 *
 * NOTE!  There are restrictions about when you can call this method.  If
 * you did a sysThreadAlloc, then you can call this method as soon as
 * sysThreadAlloc returns.  If you called sysThreadCreate(start_function),
 * then you must call sysThreadStackTop only inside start_function and not
 * as soon as sysThreadCreate returns.
 */
void *
sysThreadStackTop(sys_thread_t *tid)
{
    return tid->stack_top;
}

long *
sysThreadRegs(sys_thread_t * tid, int *nregs)
{
    *nregs = N_TRACED_REGS;
    return tid->regs;
}

static void *
_start(void *tid_)
{
    sys_thread_t *tid = (sys_thread_t *)tid_;

    np_initialize_thread(tid);

#ifdef __linux__
    /*
     * Disable cancellation.  The default cancel type is
     * PTHREAD_CANCEL_DEFERRED, so if we set the cancel state to
     * PTHREAD_CANCEL_ENABLE again, we'll get deferred cancellation.
     */
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

    tid->sp = 0;
    thr_setspecific(tid_key, tid);
    thr_setspecific(intrJmpbufkey, NULL); /* paranoid */
    np_stackinfo(&tid->stack_bottom, &tid->stack_size);
    tid->stack_top = (void *)((char *)(tid->stack_bottom) - tid->stack_size);

    /* Wait for resume signal */
    np_initial_suspend(tid);
#else
#ifdef  USE_PTHREADS
#ifndef USE_MUTEX_HANDSHAKE
    /* Wait for semaphore to be posted once thread has been suspended */
    sem_wait(&tid->sem);
    sem_destroy(&tid->sem);
#else
    /* I'm a new thread, and I must co-operate so I can be suspended. */
    pthread_mutex_lock(&tid->ntcond.m);
    tid->ntcond.state = NEW_THREAD_REQUESTED_SUSPEND;
    pthread_cond_signal(&tid->ntcond.c);
    while (tid->ntcond.state != NEW_THREAD_SUSPENDED)
        pthread_cond_wait(&tid->ntcond.c, &tid->ntcond.m);
    pthread_mutex_unlock(&tid->ntcond.m);
#endif /* USE_MUTEX_HANDSHAKE */
#endif /* USE_PTHREADS */
#endif /* !linux */
    if (profiler_on) {
        np_profiler_init(tid);
    }

#ifndef __linux__
    tid->sp = 0;
    thr_setspecific(tid_key, tid);
#endif

    tid->state = RUNNABLE;

#ifndef __linux__
    np_stackinfo(&tid->stack_bottom, &tid->stack_size);
    tid->stack_top = (void *)((char *)(tid->stack_bottom) - tid->stack_size);
#endif

    setFPMode();
    tid->start_proc(tid->start_parm);
#ifdef __linux__
    /* Paranoid: We don't want to be canceled now, it would have
       unpredictable consequences */
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
#endif

    sysThreadFree();
    thr_exit(0);
    /* NOT REACHED */
    return 0;
}

int
sysThreadCreate(sys_thread_t **tidP, long ss, void (*start)(void *), void *arg)
{
    size_t stack_size = ss;
    int err;
    sys_thread_t *tid = allocThreadBlock();
#ifdef USE_PTHREADS
    pthread_attr_t attr;
#endif

    if (tid == NULL) {
        return SYS_NOMEM;
    }
    *tidP = tid;

#ifdef __linux__
    memset((char *)tid, 0, sizeof(sys_thread_t));
#endif
    /* Install the backpointer to the Thread object */

#ifdef __linux__
    tid->interrupted = tid->pending_interrupt = FALSE;
#else
    tid->interrupted = FALSE;
#endif
    tid->onproc = FALSE;

#ifndef __linux__
    SYS_QUEUE_LOCK(sysThreadSelf());
    ActiveThreadCount++;        /* Global thread count */
    tid->next = ThreadQueue;    /* Chain all threads */
    ThreadQueue = tid;
    SYS_QUEUE_UNLOCK(sysThreadSelf());
#endif

    tid->start_proc = start;
    tid->start_parm = arg;
#ifdef __linux__
    tid->state = SUSPENDED;
#endif

#ifdef __linux__
    tid->primordial_thread = 0;

    /* Semaphore used to block thread until np_suspend() is called */
    err = sem_init(&tid->sem_suspended, 0, 0);
    sysAssert(err == 0);
    /* Thread attributes */
    pthread_attr_init(&attr);
#ifdef _POSIX_THREAD_ATTR_STACKSIZE
    pthread_attr_setstacksize(&attr, stack_size);
#endif
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    if (profiler_on) {
      pthread_attr_setscope(&attr, PTHREAD_SCOPE_SYSTEM);
    }
    /* Create the thread. The thread will block waiting to be suspended */
    err = pthread_create(&tid->sys_thread, &attr, _start, (void *)tid);
    sysAssert(err == 0);
    if (err == 0) {
        err = sem_wait(&tid->sem_suspended);
        if (err == 0) {
            sem_destroy(&tid->sem_suspended);
        }
    }
    sysAssert(err == 0);

    SYS_QUEUE_LOCK(sysThreadSelf());
    ActiveThreadCount++;        /* Global thread count */
    tid->next = ThreadQueue;    /* Chain all threads */
    ThreadQueue = tid;
    SYS_QUEUE_UNLOCK(sysThreadSelf());
#else
#ifdef USE_PTHREADS

#ifndef USE_MUTEX_HANDSHAKE
    /* Semaphore used to block thread until np_suspend() is called */
    err = sem_init(&tid->sem, 0, 0);
    sysAssert(err == 0);
    /* Thread attributes */
#else
    /* Setup condition required to suspend the newly created thread. */
    pthread_mutex_init(&tid->ntcond.m, NULL);
    pthread_cond_init(&tid->ntcond.c, NULL);
    tid->ntcond.state = NEW_THREAD_MUST_REQUEST_SUSPEND;
    pthread_mutex_lock(&tid->ntcond.m);
#endif /* USE_MUTEX_HANDSHAKE */

    /* Create the new thread. */
    pthread_attr_init(&attr);
#ifdef _POSIX_THREAD_ATTR_STACKSIZE
    pthread_attr_setstacksize(&attr, stack_size);
#endif
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    if (profiler_on)
        pthread_attr_setscope(&attr, PTHREAD_SCOPE_SYSTEM);
    err = pthread_create(&tid->sys_thread, &attr, _start, (void *)tid);

#ifndef USE_MUTEX_HANDSHAKE
    if (err == 0) {
       /* Suspend the thread */
       err = np_suspend(tid);
       if (err == SYS_OK) {
           /* Unblock the thread now that it has been suspended */
           err = sem_post(&tid->sem);
           sysAssert(err == 0);
       }
    }
#else
    /* Wait for the newly created thread to block. */
    while (tid->ntcond.state != NEW_THREAD_REQUESTED_SUSPEND)
        pthread_cond_wait(&tid->ntcond.c, &tid->ntcond.m);

    /* So it blocked.  Now suspend it and _then_ get it out of the block. */
    np_suspend(tid->sys_thread);
    tid->ntcond.state = NEW_THREAD_SUSPENDED;
    pthread_cond_signal(&tid->ntcond.c);
    pthread_mutex_unlock(&tid->ntcond.m);
#endif /* USE_MUTEX_HANDSHAKE */

#else
    /* Create the thread */
    err = thr_create(NULL, stack_size, _start, (void *)tid,
                     THR_SUSPENDED|THR_DETACHED|
                     (profiler_on ? THR_BOUND : 0),
                     &tid->sys_thread);
#endif /* USE_PTHREADS */
#endif /* !linux */

    tid->state = SUSPENDED;
    sysAssert(err != EINVAL);   /* Invalid argument: shouldn't happen */
    if (err == EAGAIN) {
        err = SYS_NORESOURCE;   /* Will be treated as though SYS_NOMEM */
    } else if (err == ENOMEM) {
        err = SYS_NOMEM;
    } else {
        err = SYS_OK;
    }

    return err;
}

/*
 * Free a system thread block.
 * Remove from the thread queue.
 */
int
sysThreadFree()
{
    sys_thread_t *tid = sysThreadSelf();
    /*
     * remove ourselves from the thread queue.  This must be done after
     * the notify above since monitor operations aren't really safe if
     * your thread isn't on the thread queue.  (This isn't true of
     * the sysMonitor* functions, only monitor*)
     */
    SYS_QUEUE_LOCK(tid);
    removeFromActiveQ(tid);
    SYS_QUEUE_UNLOCK(tid);

    /* For invocation API: later sysThreadSelf() calls will return 0 */
    thr_setspecific(tid_key, 0);

#ifdef __linux__
    np_free_thread(tid);
#endif

    freeThreadBlock(tid);
    return SYS_OK;
}

/*
 * Current thread yield control
 *
 * Green threads originally supported forcing another thread to yield...
 */
void
sysThreadYield()
{
#ifndef __linux__
    thr_yield();
#endif
}

#ifdef USE_PTHREADS
/*
 * For POSIX threads, we don't want to use real-time policies SCHED_FIFO or
 * SCHED_RR.  That leaves SCHED_OTHER which is implementation defined.  We
 * assume Solaris-pthreads like behavior for SCHED_OTHER, and if it doesn't
 * work on your platform, then maybe you want to do turn off thread
 * priorities by setting -DMOOT_PRIORITIES.
 */
#ifndef MOOT_PRIORITIES
#define USE_SCHED_OTHER
#endif /* MOOT_PRIORITIES */
#endif /* USE_PTHREADS */

/*
 * Get the scheduling priority of a specified thread
 */
int
sysThreadGetPriority(sys_thread_t * tid, int *pri)
{
#ifdef USE_PTHREADS
#ifdef USE_SCHED_OTHER
    struct sched_param param;
    int policy = SCHED_OTHER;
    param.sched_priority = *pri;
    return pthread_getschedparam(tid->sys_thread, &policy, &param);
#else
    return 0;
#endif /* USE_SCHED_OTHER */
#else
    return thr_getprio(tid->sys_thread, pri);
#endif /* USE_PTHREADS */
}


/*
 * Set the scheduling priority of a specified thread
 */
int
sysThreadSetPriority(sys_thread_t * tid, int pri)
{
    int err;
#ifdef USE_PTHREADS
#ifdef USE_SCHED_OTHER
    struct sched_param param;
    param.sched_priority = pri;
    err = pthread_setschedparam(tid->sys_thread, SCHED_OTHER, &param);
#else
    err = 0;
#endif /* USE_SCHED_OTHER */
#else
    err = thr_setprio(tid->sys_thread, pri);
#endif /* USE_PTHREADS */
    sysAssert(err != ESRCH);        /* No such thread: shouldn't happen */
    sysAssert(err != EINVAL);       /* Invalid arguments: shouldn't happen */
    return SYS_OK;
}

/*
 * Suspend execution of the specified thread
 */
int
sysThreadSuspend(sys_thread_t * tid)
{
    int err1 = 0;
    int err2 = 0;
    sys_thread_t *self = sysThreadSelf();

    if (tid == self) {
        self->state = SUSPENDED;
    } else {
#ifndef __linux__
        mutexLock(&tid->mutex);
#endif
        switch(tid->state) {
            case RUNNABLE:
                tid->state = SUSPENDED;
                break;
            case CONDVAR_WAIT:
                tid->state = SUSPENDED;
                tid->cpending_suspend = 1;
                break;
            case SUSPENDED:
            default:
                err1 = -1;              /* Thread in inconsistent state */
                break;
        }
#ifndef __linux__
        mutexUnlock(&tid->mutex);
#endif
    }
    if (err1 == 0) {
        err2 = np_suspend(tid);
    }

    return ((err1 == 0 && err2 == 0) ? SYS_OK : SYS_ERR);
}

/*
 * Resume execution of the specified thread
 */
int
sysThreadResume(sys_thread_t * tid)
{
    int err1 = 0;
    int err2 = 0;

#ifndef __linux__
    mutexLock(&tid->mutex);
#endif
    if (tid->cpending_suspend) {
        tid->cpending_suspend = 0;
        tid->state = CONDVAR_WAIT;
    } else {
        switch(tid->state) {
            case SUSPENDED:
                tid->state = RUNNABLE;
                break;
            case RUNNABLE:
            case CONDVAR_WAIT:
            default:
                err1 = -1;              /* Thread in inconsistent state */
                break;
        }
    }
#ifndef __linux__
    mutexUnlock(&tid->mutex);
#endif
    if (err1 == 0) {
        err2 = np_continue(tid);
    }

    return ((err1 == 0 && err2 == 0) ? SYS_OK : SYS_ERR);
}

/*
 * Return the sys_thread_t * of the calling thread
 */
sys_thread_t *
sysThreadSelf()
{
#ifdef USE_PTHREADS
    return pthread_getspecific(tid_key);
#else
    sys_thread_t * tid=NULL;
    int err = thr_getspecific(tid_key, (void *) &tid);

    if (err == 0) {
        return tid;
    }

    sysAssert(tid_key == -1 || err != 0);

    return NULL;
#endif
}

/*
 * Enumerate over all threads, calling a function for each one.  A
 * paranoid helper function would be prepared to deal with threads
 * that have not been created by Java.
 */

int
sysThreadEnumerateOver(int (*func)(sys_thread_t *, void *), void *arg)
{
    sys_thread_t *tid;
    int err = SYS_OK;
    int i;

    sysAssert(SYS_QUEUE_LOCKED(sysThreadSelf()));

    tid = ThreadQueue;
    for (i = 0; i < ActiveThreadCount && tid != 0; i++) {
        if ((err = (*func)(tid, arg)) != SYS_OK) {
            break;
        }
        tid = tid->next;
    }

    return err;
}

void *
sysThreadNativeID(sys_thread_t *tid)
{
    return (void *) tid->sys_thread;
}

/*
 * Remove this thread from the list of Active threads.
 */
static void
removeFromActiveQ(sys_thread_t * t)
{
    sys_thread_t *prev;
    sys_thread_t *tid;

    sysAssert(SYS_QUEUE_LOCKED(sysThreadSelf()));

    ActiveThreadCount--;

    prev = 0;
    tid = ThreadQueue;
    while (tid) {
        if (tid == t) {
            if (prev) {
                prev->next = tid->next;
            } else {
                ThreadQueue = tid->next;
            }
            tid->next = 0;
            break;
        }
        prev = tid;
        tid = tid->next;
    }
}

/*
 * The mechanics of actually signalling an exception (in the future,
 * and Alarm or Interrupt) depend upon what thread implementation you
 * are using.
 */
void
sysThreadPostException(sys_thread_t *tid, void *exc)
{
    /* Thread.stop is deprecated */
    /* No longer wake up the thread if it is sleeping */
    /* thr_kill(tid->sys_thread, SIGUSR1); */
}

/*
 * Support for (Java-level) interrupts.
 */
void
sysThreadInterrupt(sys_thread_t *tid)
{
#ifdef __linux__
    tid->pending_interrupt = TRUE;
    pthread_cancel(tid->sys_thread);
#else
    mutexLock(&tid->mutex);
    tid->interrupted = TRUE;
    mutexUnlock(&tid->mutex);
    thr_kill(tid->sys_thread, SIGUSR1);
#endif
}

/* This doesn't need to aquire any locks */
int
sysThreadIsInterrupted(sys_thread_t *tid, int ClearInterrupted)
{
    bool_t interrupted;

#ifndef __linux__
    mutexLock(&tid->mutex);
#endif
#ifdef __linux__
    interrupted = tid->pending_interrupt || tid->interrupted;

    if (ClearInterrupted == 1 && tid->pending_interrupt) {
        sys_thread_t* self = sysThreadSelf();

        if (self == tid && pthread_getspecific(intrJmpbufkey) == NULL) {
            jmp_buf jmpbuf;

            /*
             * Register our intrHandler as a cleanup handler.  If we get
             * interrupted (i.e. canceled), we longjmp out of this handler.
             */
            pthread_cleanup_push(intrHandler, NULL);
            if (setjmp(jmpbuf) == 0) {
                thr_setspecific(intrJmpbufkey, &jmpbuf);
                pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
                while (1) { pthread_testcancel(); }
            }
            /* Remove intrHandler without calling it. */
            pthread_cleanup_pop(0);
        }
    }

    if (ClearInterrupted == 1 && interrupted) {
        /* we have do to this last, otherwise we really would cancel the
         thread */
      tid->interrupted = FALSE;
    }
#else
    interrupted = tid->interrupted;
    if (ClearInterrupted == 1) {
        tid->interrupted = FALSE;
        mutexUnlock(&tid->mutex);
         if (interrupted) {
             sigset_t osigset;
             /*
              * we were interrupted so we may have a signal pending that
              * we need to clear.  We can just temporarily unmask SIGUSR1
              * and the sigusr1Handler to catch and notice that the
              * interrupted flag is not set.
              */

             thr_setspecific(sigusr1Jmpbufkey, NULL); /* paranoid */
             thr_sigsetmask(SIG_UNBLOCK, &sigusr1Mask, &osigset);
             thr_sigsetmask(SIG_SETMASK, &osigset, NULL);
         }
     } else {   /* Otherwise leave it alone */
       mutexUnlock(&tid->mutex);
     }
#endif
    return interrupted;
}



/*
 * Stop all threads other than the current one.  The stopped threads
 * may be restarted with sysThreadMulti(); the operation of this
 * routine is atomic; it either stops all java threads or it stops
 * none of them.  Upon success (all threads stopped) this routine
 * returns SYS_OK, otherwise SYS_ERR.
 *
 * In general, sysThreadSingle() should take care of anything below
 * the HPI that needs to be done to safely run single-threaded.
 */
int
sysThreadSingle()
{
    return np_single();
}

/*
 * Allow multi threaded execution to resume after a
 * sysThreadSingle() call.
 *
 * Note: When this routine is called the scheduler should already
 * have been locked by sysThreadSingle().
 */
void
sysThreadMulti()
{
    np_multi();
}

#ifdef __linux__
/*
 * We abuse thread cancellation to interrupt the threads, i.e when an
 * exception is posted against the thread, pthread_cancel(3) is sent to the
 * thread and the canceled thread executes the following cleanup handler
 */
void
intrHandler(void* arg)
{
    jmp_buf* jmpbufp = pthread_getspecific(intrJmpbufkey);
    if (jmpbufp != NULL) {
        volatile sys_thread_t* self = sysThreadSelf();
        pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
        pthread_setcanceltype(PTHREAD_CANCEL_DEFERRED, NULL);
        self->interrupted = TRUE;
        self->pending_interrupt = FALSE;
        thr_setspecific(intrJmpbufkey, NULL);
        longjmp(*jmpbufp, 1);
    } else {
#ifdef PARANOID_DEBUG
        sysAssert(0);
#endif
    }
}

#else
/*
 * SIGUSR1 is used to interrupt the threads, i.e when an exception
 * is posted against the thread, SIGUSR1 is sent to the thread and the
 * thread gets the signal, executes the following handler
 */

static void
#ifdef SA_SIGINFO
sigusr1Handler(int sig, siginfo_t *info, void *uc)
#else
sigusr1Handler(int sig)
#endif
{
    sys_thread_t *tid = sysThreadSelf();

    if (tid->interrupted) {
        sigjmp_buf *jmpbufp;
#ifdef USE_PTHREADS
        jmpbufp = pthread_getspecific(sigusr1Jmpbufkey);
#else
        thr_getspecific(sigusr1Jmpbufkey, (void **)&jmpbufp);
#endif
        if (jmpbufp != NULL)
            siglongjmp(*jmpbufp, 1);
    }
}
#endif

HPI_SysInfo *
sysGetSysInfo()
{
    static HPI_SysInfo info = {0, 0};

    if (info.name == NULL) {
        /*
         * we want the number of processors configured not the number online
         * since processors may be turned on and off dynamically.
         */
        int cpus = (int) sysconf(_SC_NPROCESSORS_CONF);

        info.isMP = (cpus < 0) ? 1 : (cpus > 1);
        info.name = "native threads";
    }
    return &info;
}


jlong
sysThreadCPUTime()
{
#ifdef HAVE_GETHRVTIME
   return gethrvtime();
#else
   return 0;
#endif
}

int
sysThreadGetStatus(sys_thread_t *tid, sys_mon_t **monitorPtr)
{
    int status;
    switch (tid->state) {
      case RUNNABLE:
          if (tid->mon_enter) {
              status = SYS_THREAD_MONITOR_WAIT;
          } else {
              status = SYS_THREAD_RUNNABLE;
          }
          break;
      case SUSPENDED:
          if (tid->mon_enter)
              status = SYS_THREAD_SUSPENDED | SYS_THREAD_MONITOR_WAIT;
          else if (tid->cpending_suspend)
              status = SYS_THREAD_SUSPENDED | SYS_THREAD_CONDVAR_WAIT;
          else
              status = SYS_THREAD_SUSPENDED;
          break;
      case CONDVAR_WAIT:
          status = SYS_THREAD_CONDVAR_WAIT;
          break;
      default:
        return SYS_ERR;
    }
    if (monitorPtr) {
        if (status & SYS_THREAD_MONITOR_WAIT) {
            *monitorPtr = tid->mon_enter;
        } else if (status & SYS_THREAD_CONDVAR_WAIT) {
            *monitorPtr = tid->mon_wait;
        } else {
            *monitorPtr = NULL;
        }
    }
    return status;
}

int sysAdjustTimeSlice(int new)
{
    return SYS_ERR;
}

void sysThreadProfSuspend(sys_thread_t *tid)
{
    np_profiler_suspend(tid);
}

void sysThreadProfResume(sys_thread_t *tid)
{
    np_profiler_continue(tid);
}

bool_t sysThreadIsRunning(sys_thread_t *tid)
{
    return np_profiler_thread_is_running(tid);
}

void *
sysThreadInterruptEvent()
{
    return NULL;
}
