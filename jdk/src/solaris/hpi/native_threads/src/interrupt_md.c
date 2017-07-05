/*
 * Copyright (c) 1994, 2006, Oracle and/or its affiliates. All rights reserved.
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
 * Solaris 2.X dependant interrupt handling code.
 */

/*
 * Header files.
 */
#include <stdio.h>
#include <signal.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>

#include "hpi_impl.h"

#include "mutex_md.h"
#include "condvar_md.h"
#include "monitor_md.h"
#include "threads_md.h"
#include "interrupt.h"

static int pending_signals[N_INTERRUPTS];

/* Stubs used from interrupt.c: they are nontrivial on Green */
void intrLock() {}
void intrUnlock() {}
#ifdef __linux__
extern int sr_sigsusp;
extern int sr_sigresu;
#endif

/* We need a special monitor implementation for signals because
 * signal handlers are not necessarily called in a Java thread.
 */
struct {
    thread_t owner;
    unsigned int count;
    mutex_t mutex;
    condvar_t condvar;
} userSigMon;

static void sigMonitorInit()
{
    userSigMon.owner = 0;
    userSigMon.count = 0;
    mutexInit(&userSigMon.mutex);
    condvarInit(&userSigMon.condvar);
}

static void sigMonitorEnter()
{
    thread_t self = thr_self();

    if (userSigMon.owner == self) {
        userSigMon.count++;
    } else {
        mutex_lock(&userSigMon.mutex);
        userSigMon.owner = self;
        userSigMon.count = 1;
    }
}

static void sigMonitorExit()
{
    thread_t self = thr_self();

    sysAssert(userSigMon.owner == self);
    sysAssert(userSigMon.count > 0);
    if (--userSigMon.count == 0) {
        userSigMon.owner = 0;
        mutex_unlock(&userSigMon.mutex);
    }
}

static void sigMonitorNotify()
{
    thread_t self = thr_self();

    sysAssert(userSigMon.owner == self);
    sysAssert(userSigMon.count > 0);
    condvarSignal(&userSigMon.condvar);
}

static void sigMonitorWait()
{
    thread_t self = thr_self();

    unsigned int saved_count = userSigMon.count;

    sysAssert(userSigMon.owner == self);
    sysAssert(userSigMon.count > 0);

    userSigMon.count = 0;
    userSigMon.owner = 0;

    condvarWait(&userSigMon.condvar, &userSigMon.mutex, CONDVAR_WAIT);

    sysAssert(userSigMon.owner == 0);
    sysAssert(userSigMon.count == 0);

    userSigMon.count = saved_count;
    userSigMon.owner = self;
}

static int
my_sigignore(int sig)
{
#ifndef HAVE_SIGIGNORE
    struct sigaction action;
    sigset_t set;

    action.sa_handler = SIG_IGN;
    action.sa_flags = 0;
    sigemptyset(&action.sa_mask);

    if (sigaction(sig, &action, (struct sigaction *)0) < 0)
        return -1;
    sigemptyset(&set);
    if (sigaddset(&set, sig) < 0)
        return -1;
    return sigprocmask(SIG_UNBLOCK, &set, (sigset_t *)0);
#else
    return sigignore(sig);
#endif /* HAVE_SIGIGNORE */
}


/*
 * intrInitMD() -- Target-specific initialization.
 */
extern void
intrInitMD()
{
    memset(pending_signals, 0, sizeof(pending_signals));
    (void)my_sigignore(SIGPIPE);
    sigMonitorInit();
}

/*-
 * intrDispatchMD() -- Turn our signal into an intrDispatch().
 */
void
#ifdef SA_SIGINFO
intrDispatchMD(int sig, siginfo_t *info, void *uc)
#else
intrDispatchMD(int sig)
#endif /* SA_SIGINFO */
{
    Log1(1, "signalHandlerDispatch(sig=%d)\n", sig);

    sigMonitorEnter();
#ifdef SA_SIGINFO
#if defined(__linux__) && defined(__sparc__)
    uc = (((char *)&sig) + 4 + 0x20);
    info = (siginfo_t *)(((char *)uc) + 0x60);
#endif
    intrDispatch(sig, info, uc);
#else
    intrDispatch(sig, 0, 0);
#endif /* SA_SIGINFO */

    sigMonitorExit();
}

bool_t intrInUse(int sig)
{
    /* Signals used in native threads implementation. */
#ifdef __linux__
    return sig == SIGPIPE || sig == sr_sigsusp || sig == sr_sigresu;
#else
    return sig == SIGPIPE || sig == SIGUSR1;
#endif
}

void sysSignalNotify(int sig)
{
    sigMonitorEnter();
    pending_signals[sig]++;
    sigMonitorNotify();
    sigMonitorExit();
}

static int lookupSignal()
{
    int i;
    for (i = 0; i < N_INTERRUPTS; i++) {
        if (pending_signals[i]) {
            pending_signals[i]--;
            return i;
        }
    }
    return -1;
}

int sysSignalWait()
{
    int sig;
    sigMonitorEnter();
    while ((sig = lookupSignal()) == -1) {
        sigMonitorWait();
    }
    sigMonitorExit();
    return sig;
}
