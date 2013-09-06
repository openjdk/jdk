/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2013 SAP AG. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

/* CopyrightVersion 1.2 */

/* This is a special library that should be loaded before libc &
 * libthread to interpose the signal handler installation functions:
 * sigaction(), signal(), sigset().
 * Used for signal-chaining. See RFE 4381843.
 */

#include <signal.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>

#define bool int
#define true 1
#define false 0

// Highest so far on AIX 5.2 is SIGSAK (63)
#define MAXSIGNUM 63
#define MASK(sig) ((unsigned int)1 << sig)

static struct sigaction sact[MAXSIGNUM]; /* saved signal handlers */
static unsigned int jvmsigs = 0; /* signals used by jvm */

/* used to synchronize the installation of signal handlers */
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t cond = PTHREAD_COND_INITIALIZER;
static pthread_t tid = 0;

typedef void (*sa_handler_t)(int);
typedef void (*sa_sigaction_t)(int, siginfo_t *, void *);
// signal_t is already defined on AIX
typedef sa_handler_t (*signal_like_function_t)(int, sa_handler_t);
typedef int (*sigaction_t)(int, const struct sigaction *, struct sigaction *);

static signal_like_function_t os_signal = 0; /* os's version of signal()/sigset() */
static sigaction_t os_sigaction = 0; /* os's version of sigaction() */

static bool jvm_signal_installing = false;
static bool jvm_signal_installed = false;

static void signal_lock() {
  pthread_mutex_lock(&mutex);
  /* When the jvm is installing its set of signal handlers, threads
   * other than the jvm thread should wait */
  if (jvm_signal_installing) {
    if (tid != pthread_self()) {
      pthread_cond_wait(&cond, &mutex);
    }
  }
}

static void signal_unlock() {
  pthread_mutex_unlock(&mutex);
}

static sa_handler_t call_os_signal(int sig, sa_handler_t disp,
                                   bool is_sigset) {
  if (os_signal == NULL) {
    if (!is_sigset) {
      // Aix: call functions directly instead of dlsym'ing them
      os_signal = signal;
    } else {
      // Aix: call functions directly instead of dlsym'ing them
      os_signal = sigset;
    }
    if (os_signal == NULL) {
      printf("%s\n", dlerror());
      exit(0);
    }
  }
  return (*os_signal)(sig, disp);
}

static void save_signal_handler(int sig, sa_handler_t disp) {
  sigset_t set;
  sact[sig].sa_handler = disp;
  sigemptyset(&set);
  sact[sig].sa_mask = set;
  sact[sig].sa_flags = 0;
}

static sa_handler_t set_signal(int sig, sa_handler_t disp, bool is_sigset) {
  sa_handler_t oldhandler;
  bool sigused;

  signal_lock();

  sigused = (MASK(sig) & jvmsigs) != 0;
  if (jvm_signal_installed && sigused) {
    /* jvm has installed its signal handler for this signal. */
    /* Save the handler. Don't really install it. */
    oldhandler = sact[sig].sa_handler;
    save_signal_handler(sig, disp);

    signal_unlock();
    return oldhandler;
  } else if (jvm_signal_installing) {
    /* jvm is installing its signal handlers. Install the new
     * handlers and save the old ones. jvm uses sigaction().
     * Leave the piece here just in case. */
    oldhandler = call_os_signal(sig, disp, is_sigset);
    save_signal_handler(sig, oldhandler);

    /* Record the signals used by jvm */
    jvmsigs |= MASK(sig);

    signal_unlock();
    return oldhandler;
  } else {
    /* jvm has no relation with this signal (yet). Install the
     * the handler. */
    oldhandler = call_os_signal(sig, disp, is_sigset);

    signal_unlock();
    return oldhandler;
  }
}

sa_handler_t signal(int sig, sa_handler_t disp) {
  return set_signal(sig, disp, false);
}

sa_handler_t sigset(int sig, sa_handler_t disp) {
  return set_signal(sig, disp, true);
 }

static int call_os_sigaction(int sig, const struct sigaction  *act,
                             struct sigaction *oact) {
  if (os_sigaction == NULL) {
    // Aix: call functions directly instead of dlsym'ing them
    os_sigaction = sigaction;
    if (os_sigaction == NULL) {
      printf("%s\n", dlerror());
      exit(0);
    }
  }
  return (*os_sigaction)(sig, act, oact);
}

int sigaction(int sig, const struct sigaction *act, struct sigaction *oact) {
  int res;
  bool sigused;
  struct sigaction oldAct;

  signal_lock();

  sigused = (MASK(sig) & jvmsigs) != 0;
  if (jvm_signal_installed && sigused) {
    /* jvm has installed its signal handler for this signal. */
    /* Save the handler. Don't really install it. */
    if (oact != NULL) {
      *oact = sact[sig];
    }
    if (act != NULL) {
      sact[sig] = *act;
    }

    signal_unlock();
    return 0;
  } else if (jvm_signal_installing) {
    /* jvm is installing its signal handlers. Install the new
     * handlers and save the old ones. */
    res = call_os_sigaction(sig, act, &oldAct);
    sact[sig] = oldAct;
    if (oact != NULL) {
      *oact = oldAct;
    }

    /* Record the signals used by jvm */
    jvmsigs |= MASK(sig);

    signal_unlock();
    return res;
  } else {
    /* jvm has no relation with this signal (yet). Install the
     * the handler. */
    res = call_os_sigaction(sig, act, oact);

    signal_unlock();
    return res;
  }
}

/* The three functions for the jvm to call into */
void JVM_begin_signal_setting() {
  signal_lock();
  jvm_signal_installing = true;
  tid = pthread_self();
  signal_unlock();
}

void JVM_end_signal_setting() {
  signal_lock();
  jvm_signal_installed = true;
  jvm_signal_installing = false;
  pthread_cond_broadcast(&cond);
  signal_unlock();
}

struct sigaction *JVM_get_signal_action(int sig) {
  /* Does race condition make sense here? */
  if ((MASK(sig) & jvmsigs) != 0) {
    return &sact[sig];
  }
  return NULL;
}
