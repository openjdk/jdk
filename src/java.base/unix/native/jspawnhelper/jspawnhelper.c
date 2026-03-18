/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>

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

int main(int argc, char *argv[]) {
    ChildStuff c;
    struct stat buf;
    /* argv[1] contains the fd number to read all the child info */
    int r, fdinr, fdinw, fdout;

#ifdef DEBUG
    jtregSimulateCrash(0, 4);
#endif

    if (argc != 3) {
        printf("Incorrect number of arguments: %d\n", argc);
        puts(usageErrorText);
        sendErrorCodeAndExit(-1, ESTEP_JSPAWN_ARG_ERROR, 0, 0);
    }

    if (strcmp(argv[1], VERSION_STRING) != 0) {
        printf("Incorrect Java version: %s\n", argv[1]);
        puts(usageErrorText);
        sendErrorCodeAndExit(-1, ESTEP_JSPAWN_VERSION_ERROR, 0, 0);
    }

    r = sscanf (argv[2], "%d:%d:%d", &fdinr, &fdinw, &fdout);
    if (r == 3 && fcntl(fdinr, F_GETFD) != -1 && fcntl(fdinw, F_GETFD) != -1) {
        fstat(fdinr, &buf);
        if (!S_ISFIFO(buf.st_mode)) {
            printf("Incorrect input pipe\n");
            puts(usageErrorText);
            sendErrorCodeAndExit(-1, ESTEP_JSPAWN_NOT_A_PIPE, fdinr, errno);
        }
    } else {
        printf("Incorrect FD array data: %s\n", argv[2]);
        puts(usageErrorText);
        sendErrorCodeAndExit(-1, ESTEP_JSPAWN_NOT_A_PIPE, fdinr, errno);
    }

    // Close the writing end of the pipe we use for reading from the parent.
    // We have to do this before we start reading from the parent to avoid
    // blocking in the case the parent exits before we finished reading from it.
    close(fdinw); // Deliberately ignore errors (see https://lwn.net/Articles/576478/).
    initChildStuff (fdinr, fdout, &c);
    // Now set the file descriptor for the pipe's writing end to -1
    // for the case that somebody tries to close it again.
    assert(c.childenv[1] == fdinw);
    c.childenv[1] = -1;
    // The file descriptor for reporting errors back to our parent we got on the command
    // line should be the same like the one in the ChildStuff struct we've just read.
    assert(c.fail[1] == fdout);

    childProcess (&c);
    return 0; /* NOT REACHED */
}
