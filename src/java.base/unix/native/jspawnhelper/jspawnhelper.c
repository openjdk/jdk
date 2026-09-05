/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>

#include "childproc.h"
#include "childproc_errorcodes.h"

extern int errno;

#define ALLOC(X,Y) { \
    void *mptr; \
    mptr = malloc (Y); \
    if (mptr == 0) { \
        sendErrorCodeAndExit (fdout, ESTEP_JSPAWN_ALLOC_FAILED, (int)Y, errno); \
    } \
    X = mptr; \
}

#ifndef VERSION_STRING
#error VERSION_STRING must be defined
#endif

/* Attempts to send an error code to the parent (which may or may not
 * work depending on whether the fail pipe exists); then exits with an
 * error code corresponding to the fail step. */
void sendErrorCodeAndExit(int failpipe_fd, int step, int hint, int errno_) {
    errcode_t errcode;
    buildErrorCode(&errcode, step, hint, errno_);
    if (failpipe_fd == -1 || !sendErrorCode(failpipe_fd, errcode)) {
        /* Write error code to stdout, in the hope someone reads this. */
        printf("jspawnhelper fail: " ERRCODE_FORMAT "\n", ERRCODE_FORMAT_ARGS(errcode));
    }
    exit(exitCodeFromErrorCode(errcode));
}

static const char* usageErrorText =
    "jspawnhelper version " VERSION_STRING "\n"
    "This command is not for general use and should "
    "only be run as the result of a call to\n"
    "ProcessBuilder.start() or Runtime.exec() in a java "
    "application\n";

/*
 * read the following off the pipefd
 * - the ChildStuff struct
 * - the SpawnInfo struct
 * - the data strings for fields in ChildStuff
 */
void initChildStuff (int fdin, int fdout, ChildStuff *c) {
    char *buf;
    SpawnInfo sp;
    int bufsize, offset=0;
    int magic;
    int res;
    const int step = ESTEP_JSPAWN_RCV_CHILDSTUFF_COMM_FAIL;
    int substep = 0;

    res = readFully (fdin, &magic, sizeof(magic));
    if (res != 4) {
        sendErrorCodeAndExit(fdout, step, substep, errno);
    }

    substep ++;
    if (magic != magicNumber()) {
        sendErrorCodeAndExit(fdout, step, substep, errno);
    }

#ifdef DEBUG
    jtregSimulateCrash(0, 5);
#endif

    substep ++;
    if (readFully (fdin, c, sizeof(*c)) != sizeof(*c)) {
        sendErrorCodeAndExit(fdout, step, substep, errno);
    }

    substep ++;
    if (readFully (fdin, &sp, sizeof(sp)) != sizeof(sp)) {
        sendErrorCodeAndExit(fdout, step, substep, errno);
    }

    bufsize = sp.argvBytes + sp.envvBytes +
              sp.dirlen + sp.parentPathvBytes;

    ALLOC(buf, bufsize);

    substep++;
    if (readFully (fdin, buf, bufsize) != bufsize) {
        sendErrorCodeAndExit(fdout, step, substep, errno);
    }

    /* Initialize argv[] */
    ALLOC(c->argv, sizeof(char *) * sp.nargv);
    initVectorFromBlock (c->argv, buf+offset, sp.nargv-1);
    offset += sp.argvBytes;

    /* Initialize envv[] */
    if (sp.nenvv == 0) {
        c->envv = 0;
    } else {
        ALLOC(c->envv, sizeof(char *) * sp.nenvv);
        initVectorFromBlock (c->envv, buf+offset, sp.nenvv-1);
        offset += sp.envvBytes;
    }

    /* Initialize pdir */
    if (sp.dirlen == 0) {
        c->pdir = 0;
    } else {
        c->pdir = buf+offset;
        offset += sp.dirlen;
    }

    /* Initialize parentPathv[] */
    ALLOC(parentPathv, sizeof (char *) * sp.nparentPathv)
    initVectorFromBlock ((const char**)parentPathv, buf+offset, sp.nparentPathv-1);
    offset += sp.parentPathvBytes;
}

#ifdef DEBUG
static void checkIsValid(int fd) {
    if (!fdIsValid(fd)) {
        puts(usageErrorText);
        sendErrorCodeAndExit(-1, ESTEP_JSPAWN_INVALID_FD, fd, errno);
    }
}
static void checkIsPipe(int fd) {
    checkIsValid(fd);
    if (!fdIsPipe(fd)) {
        puts(usageErrorText);
        sendErrorCodeAndExit(-1, ESTEP_JSPAWN_NOT_A_PIPE, fd, errno);
    }
}
static void checkFileDescriptorSetup() {
    checkIsValid(STDIN_FILENO);
    checkIsValid(STDOUT_FILENO);
    checkIsValid(STDERR_FILENO);
    checkIsPipe(FAIL_FILENO);
    checkIsPipe(CHILDENV_FILENO);
}
#endif // DEBUG

int main(int argc, char *argv[]) {
    ChildStuff c;

#ifdef DEBUG
    jtregSimulateCrash(0, 4);
#endif

    if (argc != 2) {
        printf("Incorrect number of arguments: %d\n", argc);
        puts(usageErrorText);
        sendErrorCodeAndExit(-1, ESTEP_JSPAWN_ARG_ERROR, 0, 0);
    }

    if (strcmp(argv[1], VERSION_STRING) != 0) {
        printf("Incorrect Java version: %s\n", argv[1]);
        puts(usageErrorText);
        sendErrorCodeAndExit(-1, ESTEP_JSPAWN_VERSION_ERROR, 0, 0);
    }

#ifdef DEBUG
    /* Check expected file descriptors */
    checkFileDescriptorSetup();
#endif

    initChildStuff(CHILDENV_FILENO, FAIL_FILENO, &c);

#ifdef DEBUG
    /* Not needed in spawn mode */
    assert(c.in[0] == -1 && c.in[1] == -1 &&
           c.out[0] == -1 && c.out[1] == -1 &&
           c.err[0] == -1 && c.err[1] == -1 &&
           c.fail[0] == -1 && c.fail[1] == -1 &&
           c.fds[0] == -1 && c.fds[1] == -1 && c.fds[2] == -1);
#endif

    childProcess (&c);
    return 0; /* NOT REACHED */
}
