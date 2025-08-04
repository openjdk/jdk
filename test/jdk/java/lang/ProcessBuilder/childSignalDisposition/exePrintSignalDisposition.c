/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 */

#include <errno.h>
#include "jvmti.h"
#include <signal.h>
#include <stdio.h>
#include <unistd.h>

#define SIGNALS(XX) \
    XX(SIGABRT) \
    XX(SIGALRM) \
    XX(SIGBUS) \
    XX(SIGCHLD) \
    XX(SIGCONT) \
    XX(SIGFPE) \
    XX(SIGHUP) \
    XX(SIGILL) \
    XX(SIGINT) \
    XX(SIGKILL) \
    XX(SIGPIPE) \
    XX(SIGQUIT) \
    XX(SIGSEGV) \
    XX(SIGSTOP) \
    XX(SIGTERM) \
    XX(SIGTSTP) \
    XX(SIGTTIN) \
    XX(SIGTTOU) \
    XX(SIGUSR1) \
    XX(SIGUSR2) \
    XX(SIGPOLL) \
    XX(SIGPROF) \
    XX(SIGSYS) \
    XX(SIGTRAP) \
    XX(SIGURG) \
    XX(SIGVTALRM) \
    XX(SIGXCPU) \
    XX(SIGXFSZ)

#define DEF_SIGNALS(name) { name, #name },
static const struct { int sig; const char* name; } signals[] = {
    SIGNALS(DEF_SIGNALS)
    { -1, NULL }
};

int main(int argc, char** argv) {
    printf("PID: %d\n", getpid());
    sigset_t current_mask;
    sigemptyset(&current_mask);
    if (sigprocmask(SIG_BLOCK /* ignored */, NULL, &current_mask) != 0) {
        perror("sigprocmask");
        return -1;
    }

    for (int n = 0; signals[n].sig != -1; n++) {
        printf("%s: ", signals[n].name);
        if (sigismember(&current_mask, signals[n].sig)) {
            printf("blocked ");
        }
        struct sigaction act;
        const void* handler;
        if (sigaction(signals[n].sig, NULL, &act) != 0) {
            perror("sigaction");
            return -1;
        }
        if (act.sa_flags & SA_SIGINFO) {
            handler = (void*)act.sa_sigaction;
        } else {
            handler = (void*)act.sa_handler;
        }
        if (handler == (void*)SIG_DFL) {
            printf("default ");
        } else if (handler == (void*)SIG_IGN) {
            printf("ignore ");
        } else if (handler == (void*)SIG_HOLD) {
            printf("hold ");
        } else {
            printf("%p ", handler);
        }
        printf("%X %X\n", act.sa_flags, act.sa_mask);
    }

    return 0;
}
