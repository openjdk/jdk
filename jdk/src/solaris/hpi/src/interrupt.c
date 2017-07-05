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
 * Threads interrupt dispatch
 */

#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>

#include "hpi_impl.h"

#include "interrupt.h"

/* handler_entry_t is used to keep track of the registered handlers. */
typedef struct handler_entry {
    intr_handler_t handler;
    void          *handlerArg;
} handler_entry_t;

static handler_entry_t handlerList[N_INTERRUPTS];

/* Initialize the interrupt system */
void
intrInit()
{
    memset(handlerList, 0, sizeof(handlerList));
    /*
     * Target-dependent initialization.
     */
    intrInitMD();
}

/* Add/Remove a handler for a particular interrupt */

signal_handler_t
intrRegister(int interrupt, intr_handler_t handler, void *handlerArg)
{
    struct sigaction sigAct, sigActOld;

    intrLock();

    if (handler == (intr_handler_t)SYS_SIG_IGN ||
        handler == (intr_handler_t)SYS_SIG_DFL) {
        /* If we get IGN or DFL, register that as the process signal handler,
         * and clear the handlerList entry.
         */
        sigAct.sa_handler = (void (*)(int))handler;
        sigAct.sa_flags = 0;
        sigaction(interrupt, &sigAct, &sigActOld);
        handlerList[interrupt].handler = NULL;
    } else {
        /* Otherwise, we register intrDispatchMD as the common signal handler,
         * and set the real handler in handlerList[interrupt].handler.
         */
#ifdef SA_SIGINFO
        sigAct.sa_handler = 0;
        sigAct.sa_sigaction = intrDispatchMD;
        sigAct.sa_flags = SA_SIGINFO | SA_RESTART;
#else
        sigAct.sa_handler = intrDispatchMD;
        sigAct.sa_flags = SA_RESTART;
#endif
        sigfillset(&sigAct.sa_mask);
        sigaction(interrupt, &sigAct, &sigActOld);
        handlerList[interrupt].handler = handler;
        handlerList[interrupt].handlerArg = handlerArg;
    }

    intrUnlock();

    /* If SA_SIGINFO is set, sa_sigaction is valid, otherwise sa_handler is. */
#ifdef SA_SIGINFO
    return (sigActOld.sa_flags & SA_SIGINFO) ?
        (signal_handler_t)sigActOld.sa_sigaction :
        (signal_handler_t)sigActOld.sa_handler;
#else
    return (signal_handler_t)sigActOld.sa_handler;
#endif
}

/*
 * intrDispatch -- Dispatch an interrupt.
 *
 *                 This routine is called from the low-level handlers
 *                 at interrupt time.
 */
void
intrDispatch(int interrupt, void *siginfo, void *context)
{
    /*
     * Assumptions:
     *  - Each interrupt only has one priority level associated with
     *    it.
     *  - Each handler will do enough work so that when it returns
     *    the source of the interrupt is masked.
     */
    handler_entry_t *entry = &handlerList[interrupt];
    intr_handler_t handler = entry->handler;

    if (handler) {
        (*handler)(interrupt, siginfo, context, entry->handlerArg);
        return;
    }

    /* No handler for this interrupt, log the error */
    Log1(0, "spurious interrupt %d\n", interrupt);
    return;
}

static void userSignalHandler(int sig, void *info, void *uc, void *arg)
{
    signal_handler_t handler = (signal_handler_t)arg;
    /* for now we don't change the disposition of the signal in this case */
    /* sysSignal(sig, SYS_SIG_DFL); */
    handler(sig, info, uc);
}

signal_handler_t sysSignal(int sig, signal_handler_t newHandler)
{
    handler_entry_t *entry = &handlerList[sig];
    void *oldHandlerArg = entry->handlerArg;
    signal_handler_t oldHandler;

    if (intrInUse(sig)) {
        return SYS_SIG_ERR;
    }

#ifdef __linux__
    oldHandler = intrRegister(sig, (intr_handler_t)userSignalHandler, (void *)newHandler);
#else
    oldHandler = intrRegister(sig, userSignalHandler, (void *)newHandler);
#endif
    /* If the old handler is intrDispatchMD, we get the real handler from
     * entry->handlerArg.
     */
    if (oldHandler == (signal_handler_t)intrDispatchMD) {
        oldHandler = (signal_handler_t)oldHandlerArg;
    }

    return oldHandler;
}

void sysRaise(int sig)
{
    raise(sig);
}
