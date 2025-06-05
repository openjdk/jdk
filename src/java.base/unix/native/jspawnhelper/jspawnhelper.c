/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

extern int errno;

#define ALLOC(X,Y) { \
    void *mptr; \
    mptr = malloc (Y); \
    if (mptr == 0) { \
        error (fdout, ERR_MALLOC); \
    } \
    X = mptr; \
}

#define ERR_MALLOC 1
#define ERR_PIPE 2
#define ERR_ARGS 3

#ifndef VERSION_STRING
#error VERSION_STRING must be defined
#endif

void error (int fd, int err) {
    if (write (fd, &err, sizeof(err)) != sizeof(err)) {
        /* Not sure what to do here. I have no one to speak to. */
        exit(0x80 + err);
    }
    exit (1);
}

void shutItDown() {
    fprintf(stdout, "jspawnhelper version %s\n", VERSION_STRING);
    fprintf(stdout, "This command is not for general use and should ");
    fprintf(stdout, "only be run as the result of a call to\n");
    fprintf(stdout, "ProcessBuilder.start() or Runtime.exec() in a java ");
    fprintf(stdout, "application\n");
    fflush(stdout);
    _exit(1);
}

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

    res = readFully (fdin, &magic, sizeof(magic));
    if (res != 4 || magic != magicNumber()) {
        error (fdout, ERR_PIPE);
    }

#ifdef DEBUG
    jtregSimulateCrash(0, 5);
#endif

    if (readFully (fdin, c, sizeof(*c)) != sizeof(*c)) {
        error (fdout, ERR_PIPE);
    }

    if (readFully (fdin, &sp, sizeof(sp)) != sizeof(sp)) {
        error (fdout, ERR_PIPE);
    }

    bufsize = sp.argvBytes + sp.envvBytes +
              sp.dirlen + sp.parentPathvBytes;

    ALLOC(buf, bufsize);

    if (readFully (fdin, buf, bufsize) != bufsize) {
        error (fdout, ERR_PIPE);
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
        fprintf(stdout, "Incorrect number of arguments: %d\n", argc);
        shutItDown();
    }

    if (strcmp(argv[1], VERSION_STRING) != 0) {
        fprintf(stdout, "Incorrect Java version: %s\n", argv[1]);
        shutItDown();
    }

    r = sscanf (argv[2], "%d:%d:%d", &fdinr, &fdinw, &fdout);
    if (r == 3 && fcntl(fdinr, F_GETFD) != -1 && fcntl(fdinw, F_GETFD) != -1) {
        fstat(fdinr, &buf);
        if (!S_ISFIFO(buf.st_mode)) {
            fprintf(stdout, "Incorrect input pipe\n");
            shutItDown();
        }
    } else {
        fprintf(stdout, "Incorrect FD array data: %s\n", argv[2]);
        shutItDown();
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
