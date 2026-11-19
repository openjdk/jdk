/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include <fcntl.h>
#include "sys.h"
#include "util.h"
#include "error_messages.h"

static char *skipWhitespace(char *p) {
    while ((*p != '\0') && isspace((unsigned char) *p)) {
        p++;
    }
    return p;
}

static char *skipNonWhitespace(char *p) {
    while ((*p != '\0') && !isspace((unsigned char) *p)) {
        p++;
    }
    return p;
}

static int
markCloseOnExec(int fd)
{
    const int flags = fcntl(fd, F_GETFD);
    if (flags < 0) {
        return -1;
    }
    if ((flags & FD_CLOEXEC) == 0) {
        if (fcntl(fd, F_SETFD, flags | FD_CLOEXEC) < 0) {
            return -1;
        }
    }
    return 0;
}

#if defined(_AIX)
  /* AIX does not understand '/proc/self' - it requires the real process ID */
  #define FD_DIR aix_fd_dir
#elif defined(_ALLBSD_SOURCE)
  #define FD_DIR "/dev/fd"
#else
  #define FD_DIR "/proc/self/fd"
#endif

// Marks all file descriptors found in /proc/self/fd with the
// FD_CLOEXEC flag to ensure they are automatically closed
// upon execution of a new program via exec(). This function
// returns -1 on failure and 0 on success.
static int
markDescriptorsCloseOnExec(void)
{
    DIR *dp;
    struct dirent *dirp;
    const int from_fd = STDERR_FILENO;

#if defined(_AIX)
    /* AIX does not understand '/proc/self' - it requires the real process ID */
    char aix_fd_dir[32];     /* the pid has at most 19 digits */
    snprintf(aix_fd_dir, 32, "/proc/%d/fd", getpid());
#endif

    if ((dp = opendir(FD_DIR)) == NULL) {
        ERROR_MESSAGE(("failed to open dir %s while determining"
                       " file descriptors to mark or close for process %d",
                       FD_DIR, getpid()));
        return -1; // failure
    }

    int dir_fd = dirfd(dp);

    while ((dirp = readdir(dp)) != NULL) {
        if (!isdigit(dirp->d_name[0])) {
            continue;
        }
        int fd = strtol(dirp->d_name, NULL, 10);
        if (fd <= INT_MAX && fd > from_fd && fd != dir_fd) {
            if (markCloseOnExec(fd) == -1) {
                (void)close((int)fd);
            }
        }
    }

    (void)closedir(dp);

    return 0; // success
}

// Performs necessary housekeeping in the forked child process,
// such as marking copied file descriptors (except standard input/output/error)
// with FD_CLOEXEC to ensure they are closed during exec().
// This function never returns.
static void
forkedChildProcess(const char *file, char *const argv[])
{
    /* Mark all file descriptors (except standard input/output/error)
     * copied from the parent process with FD_CLOEXEC, so they are
     * closed automatically upon exec(). */
    if (markDescriptorsCloseOnExec() < 0) { /* failed,  close the old way */
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
                       " %d file descriptors sequentially", (max_fd - i)));
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
