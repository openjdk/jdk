/*
 * Copyright 1994-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Win32 implementation of Java threads
 */

#include "hpi_impl.h"

#include <windows.h>
#include <process.h>              /* for _beginthreadex(), _endthreadex() */
#include <stdlib.h>

#include "threads_md.h"
#include "monitor_md.h"


/*
 * Queue of active Java threads
 */
static sys_thread_t *ThreadQueue;
sys_mon_t *_sys_queue_lock;

static int ActiveThreadCount = 0;               /* All threads */

/*
 * Set to TRUE once threads have been bootstrapped
 */
bool_t ThreadsInitialized = FALSE;

/*
 * Are we running under Window NT
 */
static bool_t windowsNT = FALSE;

/*
 * Thread local storage index used for looking up sys_thread_t struct
 * (tid) associated with the current thread.
 */
#define TLS_INVALID_INDEX 0xffffffffUL
static unsigned long tls_index = TLS_INVALID_INDEX;

static void RecordNTTIB(sys_thread_t *tid)
{
#ifndef _WIN64
    PNT_TIB nt_tib;
    __asm {
        mov eax, dword ptr fs:[18h];
        mov nt_tib, eax;
    }
    tid->nt_tib = nt_tib;
#else
    tid->nt_tib = 0;
#endif
}

/*
 * Add thread to queue of active threads.
 */
static void
queueInsert(sys_thread_t *tid)
{
    if (ThreadsInitialized)
        SYS_QUEUE_LOCK(sysThreadSelf());
    ActiveThreadCount++;
    tid->next = ThreadQueue;
    ThreadQueue = tid;
    if (ThreadsInitialized)
        SYS_QUEUE_UNLOCK(sysThreadSelf());
    else
        ThreadsInitialized = TRUE;
}

/*
 * Remove thread from queue of active threads.
 */
static void
removefromActiveQ(sys_thread_t *tid)
{
    sysAssert(SYS_QUEUE_LOCKED(sysThreadSelf()));
    --ActiveThreadCount;

    if (ThreadQueue == tid) {
        ThreadQueue = tid->next;
    } else {
        sys_thread_t *p;
        for (p = ThreadQueue; p->next != 0; p = p->next) {
            if (p->next == tid) {
                p->next = tid->next;
                break;
            }
        }
    }
}

/*
 * Allocate and initialize the sys_thread_t structure for an arbitary
 * native thread.
 */
int
sysThreadAlloc(sys_thread_t **tidP)
{
    HANDLE hnd = GetCurrentProcess();
    sys_thread_t *tid = allocThreadBlock();
    if (tid == NULL) {
        return SYS_NOMEM;
    }

    tid->state = RUNNABLE;
    tid->interrupted = FALSE;
    tid->interrupt_event = CreateEvent(NULL, TRUE, FALSE, NULL);
    tid->id = GetCurrentThreadId();
    DuplicateHandle(hnd, GetCurrentThread(), hnd, &tid->handle, 0, FALSE,
                    DUPLICATE_SAME_ACCESS);

    RecordNTTIB(tid);
    /* For the Invocation API:
       We update the thread-specific storage before locking the
       queue because sysMonitorEnter will access sysThreadSelf.
     */
    TlsSetValue(tls_index, tid);

    queueInsert(tid);
    tid->stack_ptr = &tid;
    *tidP = tid;
    return SYS_OK;
}

/*
 * Bootstrap the Java thread system by making the current thread the
 * "primordial" thread.
 */
int threadBootstrapMD(sys_thread_t **tidP, sys_mon_t **lockP, int nb)
{
    OSVERSIONINFO windowsVersion;
    HANDLE hnd = GetCurrentProcess();

    nReservedBytes = (nb + 7) & (~7);
    /*
     * Allocate TLS index for thread-specific data.
     */
    tls_index = TlsAlloc();
    if (tls_index == TLS_INVALID_INDEX) {
        VM_CALL(jio_fprintf)(stderr, "TlsAlloc failed (errcode = %x)\n",
                    GetLastError());
        return SYS_NOMEM;
    }

    /* OS properties */
    windowsVersion.dwOSVersionInfoSize = sizeof(windowsVersion);
    GetVersionEx(&windowsVersion);
    windowsNT = windowsVersion.dwPlatformId == VER_PLATFORM_WIN32_NT;

   /* Initialize the queue lock monitor */
    _sys_queue_lock = (sys_mon_t *)sysMalloc(sysMonitorSizeof());
    if (_sys_queue_lock == NULL) {
        return SYS_ERR;
    }
    VM_CALL(monitorRegister)(_sys_queue_lock, "Thread queue lock");
    *lockP = _sys_queue_lock;

    return sysThreadAlloc(tidP);
}

/*
 * Return current stack pointer of specified thread.
 */
void *
sysThreadStackPointer(sys_thread_t *tid)
{
#ifndef _WIN64
    CONTEXT context;
    WORD __current_SS;

    /* REMIND: Need to fix this for Win95 */
    context.ContextFlags = CONTEXT_CONTROL;
    if (!GetThreadContext(tid->handle, &context)) {
        VM_CALL(jio_fprintf)(stderr, "GetThreadContext failed (errcode = %x)\n",
                GetLastError());
        return 0;
    }

    /* With the NT TIB stuff that Hong came up with, I don't think we
     * need any of the complicated VirtualQuery calls anymore. If
     * context.Esp is within the stack limit and base, we return
     * context.Esp, otherwise, we can simply return nt_tib->StackLimit.
     * To minimize code changes, though, I'm keeping the code the way
     * it was.
     */
    if (tid->nt_tib == NULL) {
        /* thread hasn't started yet. */
        return 0;
    }

    __asm {
        mov ax, ss;
        mov __current_SS, ax;
    }

    if (context.SegSs == __current_SS &&
        context.Esp >= (uintptr_t)(tid->nt_tib->StackLimit) &&
        context.Esp < (uintptr_t)(tid->nt_tib->StackBase)) {
        MEMORY_BASIC_INFORMATION mbi;

        VirtualQuery((PBYTE) context.Esp, &mbi, sizeof(mbi));

        if (!(mbi.Protect & PAGE_GUARD)) {
            return (void *) context.Esp;
        } else {
            SYSTEM_INFO si;
            char *Esp = (char*) context.Esp;
            DWORD dwPageSize;

            GetSystemInfo(&si);
            dwPageSize = si.dwPageSize;
            Esp -= (((DWORD) Esp) % dwPageSize);
            do {
                Esp += dwPageSize;
                VirtualQuery((PBYTE) Esp, &mbi, sizeof(mbi));
            } while (mbi.Protect & PAGE_GUARD);
            return Esp;
        }
    } else {
        /* segment selectors don't match - thread is in some weird context */
        MEMORY_BASIC_INFORMATION mbi;
        PBYTE pbStackHwm, pbStackBase;
        SYSTEM_INFO si;
        DWORD dwPageSize;
        stackp_t stack_ptr = tid->stack_ptr;

        if (stack_ptr == 0) {
            return 0;
        }
        GetSystemInfo(&si);
        dwPageSize = si.dwPageSize;
        VirtualQuery((PBYTE)stack_ptr - 1, &mbi, sizeof(mbi));
        pbStackBase = (PBYTE)mbi.AllocationBase;
        /* step backwards till beginning of segment, non-RW page, or guard
           page (guard pages only on WinNT) */
        do {
            pbStackHwm = (PBYTE)mbi.BaseAddress;
            if (pbStackHwm <= pbStackBase) {
                break;
            }
            VirtualQuery(pbStackHwm - dwPageSize, &mbi, sizeof(mbi));
        }
        while ((mbi.Protect & PAGE_READWRITE) &&
              !(mbi.Protect & PAGE_GUARD));
        /* the best we can do for now is the first page of stack
           storage - it should be a stack high-water mark, anyway */
        return (void *)pbStackHwm;
    }
#else
    return 0;
#endif
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
  return 0; /* FIXME: Unimplemented. */
}

long *
sysThreadRegs(sys_thread_t *tid, int *nregs)
{
    *nregs = N_TRACED_REGS;
    return tid->regs;
}

/*
 * Thread start routine for new Java threads
 */
static unsigned __stdcall
_start(sys_thread_t *tid)
{
    /* Should thread suspend itself at this point? */

    tid->state = RUNNABLE;
    RecordNTTIB(tid);
    TlsSetValue(tls_index, tid);
    tid->stack_ptr = &tid;
    tid->start_proc(tid->start_parm);
    sysThreadFree();
    _endthreadex(0);
    /* not reached */
    return 0;
}

/*
 * Create a new Java thread. The thread is initially suspended.
 */
int
sysThreadCreate(sys_thread_t **tidP, long stack_size,
                void (*proc)(void *), void *arg)
{
    sys_thread_t *tid = allocThreadBlock();
    if (tid == NULL) {
        return SYS_NOMEM;
    }
    tid->state = SUSPENDED;
    tid->start_proc = proc;
    tid->start_parm = arg;

    tid->interrupt_event = CreateEvent(NULL, TRUE, FALSE, NULL);

    /*
     * Start the new thread.
     */
    tid->handle = (HANDLE)_beginthreadex(NULL, stack_size, _start, tid,
                                         CREATE_SUSPENDED, &tid->id);
    if (tid->handle == 0) {
        return SYS_NORESOURCE;  /* Will be treated as though SYS_NOMEM */
    }

    queueInsert(tid);
    *tidP = tid;
    return SYS_OK;
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
    removefromActiveQ(tid);
    SYS_QUEUE_UNLOCK(tid);

    /* For invocation API: later sysThreadSelf() calls will return 0 */
    TlsSetValue(tls_index, 0);

    /*
     * Close the thread and interrupt event handles, and free the
     * sys_thread_t structure.
     */
    CloseHandle(tid->handle);
    CloseHandle(tid->interrupt_event);
    freeThreadBlock(tid);
    return SYS_OK;
}

/*
 * Yield control to another thread.
 */
void
sysThreadYield(void)
{
    Sleep(0);
}

/*
 * Suspend execution of the specified thread.
 */
int
sysThreadSuspend(sys_thread_t *tid)
{
    /* REMIND: Fix for Win95 */
    /* Set state first so state is reflected before this thread */
    /* returns.  Fix suggested by ARB of SAS  */
    thread_state_t oldstate = tid->state;
    sys_thread_t *self = sysThreadSelf();

    if (tid == self) {
        self->state = SUSPENDED;
    } else {
        switch(tid->state) {
            case RUNNABLE:
                tid->state = SUSPENDED;
                break;
            case MONITOR_WAIT:
                tid->state = SUSPENDED;
                tid->suspend_flags |= MONITOR_WAIT_SUSPENDED;
                break;
            case CONDVAR_WAIT:
                tid->state = SUSPENDED;
                tid->suspend_flags |= CONDVAR_WAIT_SUSPENDED;
                break;
            case SUSPENDED:
            case MONITOR_SUSPENDED:
            default:
                return SYS_ERR;
        }
    }
    if (SuspendThread(tid->handle) == 0xffffffffUL) {
        tid->state = oldstate;
        tid->suspend_flags = 0;
        return SYS_ERR;
    }
    return SYS_OK;
}

/*
 * Continue execution of the specified thread.
 */
int
sysThreadResume(sys_thread_t *tid)
{
    unsigned long n;

    if (tid->suspend_flags & MONITOR_WAIT_SUSPENDED) {
        tid->suspend_flags = 0;
        tid->state = MONITOR_WAIT;
    } else if (tid->suspend_flags & CONDVAR_WAIT_SUSPENDED) {
        tid->suspend_flags = 0;
        tid->state = CONDVAR_WAIT;
    } else {
        switch(tid->state) {
            case SUSPENDED:
                tid->state = RUNNABLE;
                break;
            case MONITOR_SUSPENDED:
                tid->state = MONITOR_WAIT;
                break;
            case RUNNABLE:
            case MONITOR_WAIT:
            case CONDVAR_WAIT:
            default:
                return SYS_ERR;
                break;
        }
    }

    /* Decrement thread's suspend count until no longer suspended */
    while ((n = ResumeThread(tid->handle)) > 1) {
        if (n == 0xffffffffUL) {
            return SYS_ERR;
        }
    }
    return SYS_OK;
}

/*
 * Return priority of specified thread.
 */
int
sysThreadGetPriority(sys_thread_t *tid, int *pp)
{
    switch (GetThreadPriority(tid->handle)) {
    case THREAD_PRIORITY_IDLE:
        *pp = 0; break;
    case THREAD_PRIORITY_LOWEST:
        *pp = 2; break;
    case THREAD_PRIORITY_BELOW_NORMAL:
        *pp = 4; break;
    case THREAD_PRIORITY_NORMAL:
        *pp = 5; break;
    case THREAD_PRIORITY_ABOVE_NORMAL:
        *pp = 6; break;
    case THREAD_PRIORITY_HIGHEST:
        *pp = 8; break;
    case THREAD_PRIORITY_TIME_CRITICAL:
        *pp = 10; break;
    case THREAD_PRIORITY_ERROR_RETURN:
        return SYS_ERR;
    }
    return SYS_OK;
}

/*
 * Set priority of specified thread.
 */
int
sysThreadSetPriority(sys_thread_t *tid, int p)
{
    int priority;

    switch (p) {
    case 0:
        priority = THREAD_PRIORITY_IDLE;
        break;
    case 1: case 2:
        priority = THREAD_PRIORITY_LOWEST;
        break;
    case 3: case 4:
        priority = THREAD_PRIORITY_BELOW_NORMAL;
        break;
    case 5:
        priority = THREAD_PRIORITY_NORMAL;
        break;
    case 6: case 7:
        priority = THREAD_PRIORITY_ABOVE_NORMAL;
        break;
    case 8: case 9:
        priority = THREAD_PRIORITY_HIGHEST;
        break;
    case 10:
        priority = THREAD_PRIORITY_TIME_CRITICAL;
        break;
    default:
        return SYS_ERR;
    }
    return SetThreadPriority(tid->handle, priority) ? SYS_OK : SYS_ERR;
}

/*
 * Return the thread information block of the calling thread.
 */
sys_thread_t *
sysThreadSelf(void)
{
    return tls_index == 0xffffffffUL ? 0 : TlsGetValue(tls_index);
}

/*
 * Enumerate over all threads in active queue calling a function for
 * each one.  Expects the caller to lock _queue_lock
 */
int
sysThreadEnumerateOver(int (*func)(sys_thread_t *, void *), void *arg)
{
    sys_thread_t *tid;
    int ret = SYS_OK;
    sys_thread_t *self = sysThreadSelf();

    sysAssert(SYS_QUEUE_LOCKED(sysThreadSelf()));

    for (tid = ThreadQueue; tid != 0; tid = tid->next) {
        if ((ret = (*func)(tid, arg)) != SYS_OK) {
            break;
        }
    }
    return ret;
}

/*
 * Helper function for sysThreadSingle()
 */
static int
threadSingleHelper(sys_thread_t *tid, void *self)
{
    if (tid == self) {
        return SYS_OK;
    }
    if (SuspendThread(tid->handle) == 0xffffffffUL) {
        return SYS_ERR;
    }
    {
        CONTEXT context;
        DWORD *esp = (DWORD *)tid->regs;

        context.ContextFlags = CONTEXT_INTEGER | CONTEXT_CONTROL;
        if (!GetThreadContext(tid->handle, &context)) {
            VM_CALL(jio_fprintf)
                (stderr, "GetThreadContext failed (errcode = %x)\n",
                 GetLastError());
            return SYS_ERR;
        }
#ifdef _M_AMD64
        *esp++ = context.Rax;
        *esp++ = context.Rbx;
        *esp++ = context.Rcx;
        *esp++ = context.Rdx;
        *esp++ = context.Rsi;
        *esp++ = context.Rdi;
        *esp   = context.Rbp;
#else
        *esp++ = context.Eax;
        *esp++ = context.Ebx;
        *esp++ = context.Ecx;
        *esp++ = context.Edx;
        *esp++ = context.Esi;
        *esp++ = context.Edi;
        *esp   = context.Ebp;
#endif
    }
    return SYS_OK;
}

/*
 * Puts each thread in the active thread queue to sleep except for the
 * calling thread. The threads must be later woken up with a corresponding
 * call to 'sysThreadMulti'. Returns SYS_OK on success, or SYS_ERR if any
 * of the threads could not be suspended.
 */
int
sysThreadSingle(void)
{
    return sysThreadEnumerateOver(threadSingleHelper, sysThreadSelf());
}

/*
 * Helper function for sysThreadMulti(): Only ResumeThread once, unlike
 * sysThreadResume(), which will repeatedly call ResumeThread until the
 * thread is really resumed.  That is, Thread.resume will unwind any
 * number of Thread.suspend invocations, but sysThreadMulti() calls must
 * be strictly matched with sysThreadSingle() calls.  Doing this keeps
 * the garbage collector, which uses thread suspension to stop threads
 * while it operates, from waking up threads that were already suspended
 * when GC was invoked.
 */
static int
threadMultiHelper(sys_thread_t *tid, void *self)
{
    if (tid == self || ResumeThread(tid->handle) != 0xffffffffUL) {
        return SYS_OK;
    } else {
        return SYS_ERR;
    }
}

/*
 * Wakes up each thread in active thread queue except for the calling
 * thread.  The mechanism uses thread suspension, and will not wake a
 * thread that was already suspended.  Must be matched 1-1 with calls
 * to sysThreadSingle().  Returns SYS_ERR if not all threads could be
 * woken up.
 */
void
sysThreadMulti(void)
{
    sysThreadEnumerateOver(threadMultiHelper, sysThreadSelf());
}

/*
 * Dump system-specific information about threads.
 */
void *
sysThreadNativeID(sys_thread_t *tid)
{
    return (void *)(uintptr_t)tid->id;
}

int
sysThreadCheckStack(void)
{
    return 1;
}

/*
 * The mechanics of actually signalling an exception (in the future,
 * and Alarm or Interrupt) depend upon what thread implementation you
 * are using.
 */
void
sysThreadPostException(sys_thread_t *tid, void *exc)
{
    /* Interrupt the thread if it's waiting; REMIND: race??? */
    SetEvent(tid->interrupt_event);
}

/*
 * Support for (Java-level) interrupts.
 */
void
sysThreadInterrupt(sys_thread_t *tid)
{
    if (tid->interrupted == FALSE) {
        tid->interrupted = TRUE;
        SetEvent(tid->interrupt_event);
    }
}

int
sysThreadIsInterrupted(sys_thread_t *tid, int ClearInterrupted)
{
    bool_t interrupted = tid->interrupted;
    if (interrupted && ClearInterrupted) {
        tid->interrupted = FALSE;
        ResetEvent(tid->interrupt_event);
    }
    return interrupted;
}

HPI_SysInfo *
sysGetSysInfo()
{
    static HPI_SysInfo info = {0, 0};

    if (info.name == NULL) {
        SYSTEM_INFO sysinfo;
        GetSystemInfo(&sysinfo);
        info.isMP = sysinfo.dwNumberOfProcessors > 1;
        info.name = "native threads";
    }
    return &info;
}

#define FT2INT64(ft) \
        ((jlong)(ft).dwHighDateTime << 32 | (jlong)(ft).dwLowDateTime)

jlong
sysThreadCPUTime()
{
    if (windowsNT) {
        FILETIME CreationTime;
        FILETIME ExitTime;
        FILETIME KernelTime;
        FILETIME UserTime;

        GetThreadTimes(GetCurrentThread(),
                       &CreationTime, &ExitTime, &KernelTime, &UserTime);
        return FT2INT64(UserTime) * 100;
    } else {
        return (jlong)sysGetMilliTicks() * 1000000;
    }
}

int
sysThreadGetStatus(sys_thread_t *tid, sys_mon_t **monitorPtr)
{
    int status;
    switch (tid->state) {
      case RUNNABLE:
          if (tid->enter_monitor)
              status = SYS_THREAD_MONITOR_WAIT;
          else
              status = SYS_THREAD_RUNNABLE;
          break;
      case SUSPENDED:
          if (tid->enter_monitor)
              status = SYS_THREAD_SUSPENDED | SYS_THREAD_MONITOR_WAIT;
          else if (tid->suspend_flags & CONDVAR_WAIT_SUSPENDED)
              status = SYS_THREAD_SUSPENDED | SYS_THREAD_CONDVAR_WAIT;
          else
              status = SYS_THREAD_SUSPENDED;
          break;
      case MONITOR_SUSPENDED:
          status = SYS_THREAD_SUSPENDED | SYS_THREAD_MONITOR_WAIT;
          break;
      case CONDVAR_WAIT:
          status = SYS_THREAD_CONDVAR_WAIT;
          break;
      case MONITOR_WAIT:
          /*
           * this flag should never be in used on win32 since the
           * state is actually signalled by setting self->enter_monitor
           * to point at the monitor the thread is waiting to enter
           */
          sysAssert(FALSE);
      default:
          return SYS_ERR;
    }
    if (monitorPtr) {
        if (status & SYS_THREAD_MONITOR_WAIT) {
            *monitorPtr = tid->enter_monitor;
        } else if (status & SYS_THREAD_CONDVAR_WAIT) {
            *monitorPtr = tid->wait_monitor;
        } else {
            *monitorPtr = NULL;
        }
    }
    return status;
}

int sysAdjustTimeSlice(int i)
{
    return JNI_ERR;
}

void sysThreadProfSuspend(sys_thread_t *tid)
{
    SuspendThread(tid->handle);
}

void sysThreadProfResume(sys_thread_t *tid)
{
    ResumeThread(tid->handle);
}

bool_t sysThreadIsRunning(sys_thread_t *tid)
{
#ifndef _M_AMD64
    unsigned int sum = 0;
    unsigned int *p;
    CONTEXT context;

    context.ContextFlags = CONTEXT_FULL;
    GetThreadContext(tid->handle, &context);
    p = &context.SegGs;
    while (p <= &context.SegSs) {
        sum += *p;
        p++;
    }

    if (sum == tid->last_sum) {
        return FALSE;
    }
    tid->last_sum = sum;
#endif
    return TRUE;
}

void *
sysThreadInterruptEvent()
{
    return sysThreadSelf()->interrupt_event;
}
