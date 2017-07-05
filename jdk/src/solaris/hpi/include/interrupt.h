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
 * Interrupt dispatch interface
 */

#ifndef _JAVASOFT_INTERRUPT_H_
#define _JAVASOFT_INTERRUPT_H_

/*
 * Type definitions.
 */

/*-
 * A function that handles interrupt dispatch requests is of type
 * intr_handler_t.  This type definition is mostly for convenience.
 * A declaration of a handler function, should look like this:
 *
 *   void myHandler(int interrupt, void *siginfo, void *context, void *arg);
 *
 * An intr_handler_t is constrained:
 *
 *      - It runs on the exception stack.
 *      - It cannot yield.
 *      - It cannot allocate/free memory.
 *      - It can only call interrupt-safe routines.
 *
 * "arg" is set to the "handlerArg" specified in intrRegister().
 */
typedef void (*intr_handler_t)(int interrupt, void *siginfo,
                              void *context, void *arg);

/*
 * Routines.
 */

/* Initialize the interrupt system */
void intrInit(void);

/* Set a handler for a particular interrupt */
signal_handler_t intrRegister(int interrupt, intr_handler_t handler,
                              void *handlerArg);

/* Dispatch an interrupt (called from the low-level handlers) */
void intrDispatch(int interrupt, void *siginfo, void *context);

/*-
 * The target specific header file should define the following
 *
 * Constants
 *
 *      N_INTERRUPTS  - The number of interrupt channels.  These
 *                      are numbered from 0 to (N_INTERRUPTS - 1).
 */
#ifdef __linux__
#define       N_INTERRUPTS    NSIG    /* 0 to NSIG - 1*/
#else
#define N_INTERRUPTS    32      /* 0 to 31 */
#endif

/*-
 * Routines/Macros that control whether interrupts are enabled or
 * not.
 *
 *      void intrLock(void)           - Disable all interrupts.
 *      void intrUnlock(void)         - Enable all interrupts.
 *
 *              Note: intrLock()/intrUnlock() pairs can be nested.
 *
 */

void intrLock(void);
void intrUnlock(void);

/*-
 * intrInitMD() --
 *      Initialize the machine-dependant interrupt software.
 *
 *      This routine should leave the all interrupts disabled as if
 *      one (1) intrLock() had been called.  At the end of the
 *      bootstrap, a single intrUnlock(), will be called to turn
 *      interrupts on.
 */

void intrInitMD(void);

#if defined(__solaris__) && !defined(SA_SIGINFO)
#error signal.h has not been included?
#endif

#ifdef SA_SIGINFO
/* Thread implementation dependent interrupt dispatcher. */
void intrDispatchMD(int sig, siginfo_t *info, void *uc);
#else
void intrDispatchMD(int sig);
#endif

/* Whether the signal is used by the HPI implementation */
bool_t intrInUse(int sig);

#endif /* !_JAVASOFT_INTERRUPT_H_ */
