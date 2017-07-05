/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <signal.h>
#include <dlfcn.h>
#include <thread.h>
#include <synch.h>
#include "jvm_solaris.h"

#define bool int
#define true 1
#define false 0

static struct sigaction *sact = (struct sigaction *)NULL; /* saved signal handlers */
static sigset_t jvmsigs;

/* used to synchronize the installation of signal handlers */
static mutex_t mutex = DEFAULTMUTEX;
static cond_t cond = DEFAULTCV;
static thread_t tid = 0;

typedef void (*sa_handler_t)(int);
typedef void (*sa_sigaction_t)(int, siginfo_t *, void *);
typedef sa_handler_t (*signal_t)(int, sa_handler_t);
typedef int (*sigaction_t)(int, const struct sigaction *, struct sigaction *);

static signal_t os_signal = 0; /* os's version of signal()/sigset() */
static sigaction_t os_sigaction = 0; /* os's version of sigaction() */

static bool jvm_signal_installing = false;
static bool jvm_signal_installed = false;


/* assume called within signal_lock */
static void allocate_sact() {
  size_t maxsignum;
  maxsignum = SIGRTMAX;
  if (sact == NULL) {
    sact = (struct sigaction *)malloc((maxsignum+1) * (size_t)sizeof(struct sigaction));
    memset(sact, 0, (maxsignum+1) * (size_t)sizeof(struct sigaction));
  }

  if (sact == NULL) {
    printf("%s\n", "libjsig.so unable to allocate memory");
    exit(0);
  }

  sigemptyset(&jvmsigs);
}

static void signal_lock() {
  mutex_lock(&mutex);
  /* When the jvm is installing its set of signal handlers, threads
   * other than the jvm thread should wait */
  if (jvm_signal_installing) {
    if (tid != thr_self()) {
      cond_wait(&cond, &mutex);
    }
  }
}

static void signal_unlock() {
  mutex_unlock(&mutex);
}

static sa_handler_t call_os_signal(int sig, sa_handler_t disp,
                                   bool is_sigset) {
  if (os_signal == NULL) {
    if (!is_sigset) {
      os_signal = (signal_t)dlsym(RTLD_NEXT, "signal");
    } else {
      os_signal = (signal_t)dlsym(RTLD_NEXT, "sigset");
    }
    if (os_signal == NULL) {
      printf("%s\n", dlerror());
      exit(0);
    }
  }
  return (*os_signal)(sig, disp);
}

static void save_signal_handler(int sig, sa_handler_t disp, bool is_sigset) {
  sigset_t set;
  if (sact == NULL) {
    allocate_sact();
  }
  sact[sig].sa_handler = disp;
  sigemptyset(&set);
  sact[sig].sa_mask = set;
  if (!is_sigset) {
    sact[sig].sa_flags = SA_NODEFER;
    if (sig != SIGILL && sig != SIGTRAP && sig != SIGPWR) {
      sact[sig].sa_flags |= SA_RESETHAND;
    }
  } else {
    sact[sig].sa_flags = 0;
  }
}

static sa_handler_t set_signal(int sig, sa_handler_t disp, bool is_sigset) {
  sa_handler_t oldhandler;
  bool sigblocked;

  signal_lock();
  if (sact == NULL) {
    allocate_sact();
  }

  if (jvm_signal_installed && sigismember(&jvmsigs, sig)) {
    /* jvm has installed its signal handler for this signal. */
    /* Save the handler. Don't really install it. */
    if (is_sigset) {
      /* We won't honor the SIG_HOLD request to change the signal mask */
      sigblocked = sigismember(&(sact[sig].sa_mask), sig);
    }
    oldhandler = sact[sig].sa_handler;
    save_signal_handler(sig, disp, is_sigset);

    if (is_sigset && sigblocked) {
      oldhandler = SIG_HOLD;
    }

    signal_unlock();
    return oldhandler;
  } else if (jvm_signal_installing) {
    /* jvm is installing its signal handlers. Install the new
     * handlers and save the old ones. jvm uses sigaction().
     * Leave the piece here just in case. */
    oldhandler = call_os_signal(sig, disp, is_sigset);
    save_signal_handler(sig, oldhandler, is_sigset);

    /* Record the signals used by jvm */
    sigaddset(&jvmsigs, sig);

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
    os_sigaction = (sigaction_t)dlsym(RTLD_NEXT, "sigaction");
    if (os_sigaction == NULL) {
      printf("%s\n", dlerror());
      exit(0);
    }
  }
  return (*os_sigaction)(sig, act, oact);
}

int sigaction(int sig, const struct sigaction *act, struct sigaction *oact) {
  int res;
  struct sigaction oldAct;

  signal_lock();

  if (sact == NULL ) {
    allocate_sact();
  }
  if (jvm_signal_installed && sigismember(&jvmsigs, sig)) {
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
    sigaddset(&jvmsigs, sig);

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

/* The four functions for the jvm to call into */
void JVM_begin_signal_setting() {
  signal_lock();
  jvm_signal_installing = true;
  tid = thr_self();
  signal_unlock();
}

void JVM_end_signal_setting() {
  signal_lock();
  jvm_signal_installed = true;
  jvm_signal_installing = false;
  cond_broadcast(&cond);
  signal_unlock();
}

struct sigaction *JVM_get_signal_action(int sig) {
  if (sact == NULL) {
    allocate_sact();
  }
  /* Does race condition make sense here? */
  if (sigismember(&jvmsigs, sig)) {
    return &sact[sig];
  }
  return NULL;
}

int JVM_get_libjsig_version() {
  return JSIG_VERSION_1_4_1;
}
