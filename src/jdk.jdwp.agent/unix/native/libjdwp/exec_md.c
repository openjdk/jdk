/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <dirent.h>
#include <errno.h>
#include <stdlib.h>
#include <sys/resource.h>
#include <unistd.h>
#include <string.h>
#include <ctype.h>
#include "sys.h"
#include "util.h"
#include "error_messages.h"

static char *skipWhitespace(char *p) {
    while ((*p != '\0') && isspace(*p)) {
        p++;
    }
    return p;
}

static char *skipNonWhitespace(char *p) {
    while ((*p != '\0') && !isspace(*p)) {
        p++;
    }
    return p;
}

#if defined(_AIX)
  /* AIX does not understand '/proc/self' - it requires the real process ID */
  #define FD_DIR aix_fd_dir
#elif defined(_ALLBSD_SOURCE)
  #define FD_DIR "/dev/fd"
#else
  #define FD_DIR "/proc/self/fd"
#endif

// Closes every file descriptor that is listed as a directory
// entry in "/proc/self/fd" (or its equivalent). Standard
// input/output/error file descriptors will not be closed
// by this function. This function returns 0 on failure
// and 1 on success.
int
closeDescriptors(void)
{
    DIR *dp;
    struct dirent *dirp;
    /* leave out standard input/output/error descriptors */
    int from_fd = STDERR_FILENO + 1;

    /* We're trying to close all file descriptors, but opendir() might
     * itself be implemented using a file descriptor, and we certainly
     * don't want to close that while it's in use.  We assume that if
     * opendir() is implemented using a file descriptor, then it uses
     * the lowest numbered file descriptor, just like open().  So
     * before calling opendir(), we close a couple explicitly, so that
     * opendir() can then use these lowest numbered closed file
     * descriptors afresh.   */

    close(from_fd);          /* for possible use by opendir() */
    close(from_fd + 1);      /* another one for good luck */
    from_fd += 2; /* leave out the 2 we just closed, which the opendir() may use */

#if defined(_AIX)
    /* set FD_DIR for AIX which does not understand '/proc/self' - it
     * requires the real process ID */
    char aix_fd_dir[32];     /* the pid has at most 19 digits */
    snprintf(aix_fd_dir, 32, "/proc/%d/fd", getpid());
#endif

    if ((dp = opendir(FD_DIR)) == NULL) {
        ERROR_MESSAGE(("failed to open dir %s while determining"
                       " file descriptors to close for process %d",
                       FD_DIR, getpid()));
        return 0; // failure
    }

    while ((dirp = readdir(dp)) != NULL) {
        if (!isdigit(dirp->d_name[0])) {
            continue;
        }
        const long fd = strtol(dirp->d_name, NULL, 10);
        if (fd <= INT_MAX && fd >= from_fd) {
            (void)close((int)fd);
        }
    }

    (void)closedir(dp);

    return 1; // success
}

// Does necessary housekeeping of a forked child process
// (like closing copied file descriptors) before
// execing the child process. This function never returns.
void
forkedChildProcess(const char *file, char *const argv[])
{
    /* Close all file descriptors that have been copied over
     * from the parent process due to fork(). */
    if (closeDescriptors() == 0) { /* failed,  close the old way */
        /* Find max allowed file descriptors for a process
         * and assume all were opened for the parent process and
         * copied over to this child process. We close them all. */
        const rlim_t max_fd = sysconf(_SC_OPEN_MAX);
        JDI_ASSERT(max_fd != (rlim_t)-1); // -1 represents error
        /* close(), that we subsequently call, takes only int values */
        JDI_ASSERT(max_fd <= INT_MAX);
        /* leave out standard input/output/error file descriptors */
        rlim_t i = STDERR_FILENO + 1;
        ERROR_MESSAGE(("failed to close file descriptors of"
                       " child process optimally, falling back to closing"
                       " %d file descriptors sequentially", (max_fd - i + 1)));
        for (; i < max_fd; i++) {
            (void)close(i);
        }
    }

    (void)execvp(file, argv); /* not expected to return */

    exit(errno); /* errno will have been set by the failed execvp */
}

int
dbgsysExec(char *cmdLine)
{
    int i;
    int argc;
    pid_t pid_err = (pid_t)(-1); /* this is the error return value */
    pid_t pid;
    char **argv = NULL;
    char *p;
    char *args;

    /* Skip leading whitespace */
    cmdLine = skipWhitespace(cmdLine);

    /*LINTED*/
    args = jvmtiAllocate((jint)strlen(cmdLine)+1);
    if (args == NULL) {
        return SYS_NOMEM;
    }
    (void)strcpy(args, cmdLine);

    p = args;

    argc = 0;
    while (*p != '\0') {
        p = skipNonWhitespace(p);
        argc++;
        if (*p == '\0') {
            break;
        }
        p = skipWhitespace(p);
    }

    /*LINTED*/
    argv = jvmtiAllocate((argc + 1) * (jint)sizeof(char *));
    if (argv == 0) {
        jvmtiDeallocate(args);
        return SYS_NOMEM;
    }

    for (i = 0, p = args; i < argc; i++) {
        p = skipWhitespace(p); // no-op on first iteration
        argv[i] = p;
        p = skipNonWhitespace(p);
        *p++ = '\0';
    }
    argv[i] = NULL;  /* NULL terminate */

    if ((pid = fork()) == 0) {
        // manage the child process
        forkedChildProcess(argv[0], argv);
    }
    // call to forkedChildProcess(...) will never return for a forked process
    JDI_ASSERT(pid != 0);
    jvmtiDeallocate(args);
    jvmtiDeallocate(argv);
    if (pid == pid_err) {
        return SYS_ERR;
    } else {
        return SYS_OK;
    }
}
