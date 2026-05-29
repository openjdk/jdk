/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <string.h>
#include <stddef.h>
#include <stdlib.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdbool.h>
#include <sys/wait.h>
#include "ExecCommand.h"
#include "JvmLauncher.h"


static bool logCommandLine(const char* format, const char* const argv[]) {
    char* formattedCommandLine = NULL;
    int formattedCommandLineLength = 0;
    const char* arg;
    int i;
    bool success = false;

    for (i = 0; (arg = argv[i]) != NULL; i++) {
        /* Count trailing whitespace */
        formattedCommandLineLength += strlen(arg) + 1;
        if (strchr(arg, ' ') != NULL) {
            /* Enclose the argument into single quotes */
            formattedCommandLineLength += 2;
        }
    }

    if (!formattedCommandLineLength) {
        /* Empty command line is an error */
        goto cleanup;
    }

    formattedCommandLine = malloc(formattedCommandLineLength + 1 /* \0 */);
    if (!formattedCommandLine) {
        JP_LOG_ERRNO;
        goto cleanup;
    }

    formattedCommandLine[0] = '\0';
    for (i = 0; (arg = argv[i]) != NULL; i++) {
        if (strchr(arg, ' ') != NULL) {
            strcat(formattedCommandLine, "'");
            strcat(formattedCommandLine, arg);
            strcat(formattedCommandLine, "'");
        } else {
            strcat(formattedCommandLine, arg);
        }
        strcat(formattedCommandLine, " ");
    }

    /* Trim trailing whitespace */
    formattedCommandLine[formattedCommandLineLength - 1] = '\0';

    JP_LOG_TRACE(format, formattedCommandLine);

    success = true;

cleanup:
    free(formattedCommandLine);

    return success;
}

static bool invokeCallback(
        FILE*                   stream,
        ExecCommandCallbackType callback,
        void*                   callbackData) {

    char* strBufBegin = 0;
    char* strBufEnd = 0;
    char* strBufNextChar = 0;
    char* strNewBufBegin = 0;
    size_t strBufCapacity = 0;
    int callbackMode = EXEC_CALLBACK_USE;
    int c;
    ptrdiff_t char_offset;
    int success = false;
    int tailCharCount = 0;

    for (;;) {
        c = fgetc(stream);
        if((EOF == c || '\n' == c)) {
            if (EXEC_CALLBACK_USE == callbackMode
                                            && strBufBegin != strBufNextChar) {
                *strBufNextChar = 0;
                JP_LOG_TRACE("execCommand: [%s]", strBufBegin);
                callbackMode = (*callback)(callbackData, strBufBegin);
                strBufNextChar = strBufBegin;
            }

            if (EOF == c) {
                break;
            }

            continue;
        }

        if (EXEC_CALLBACK_USE != callbackMode) {
            /* Fetch the stream */
            for (; EOF != c; c = fgetc(stream)) {
                tailCharCount++;
            }
            if (tailCharCount) {
                JP_LOG_TRACE("execCommand: fetched %d trailing chars", tailCharCount);
            }
            break;
        }

        if (strBufNextChar == strBufEnd) {
            /* Double buffer size */
            strBufCapacity = strBufCapacity * 2 + 1;
            char_offset = strBufNextChar - strBufBegin;
            strNewBufBegin = realloc(strBufBegin, strBufCapacity);
            if (!strNewBufBegin) {
                JP_LOG_ERRNO;
                goto cleanup;
            }

            strBufNextChar = strNewBufBegin + char_offset;
            strBufEnd = strNewBufBegin + strBufCapacity;
            strBufBegin = strNewBufBegin;
        }

        *strBufNextChar++ = (char)c;
    }

    success = (EXEC_CALLBACK_ERROR != callbackMode);

cleanup:
    if (strBufBegin) {
        free(strBufBegin);
    }

    return success;
}


int execCommand(
        const char* const       argv[],
        ExecCommandCallbackType callback,
        void*                   callbackData) {

    int pipefd[] = { -1, -1 };
    pid_t cpid = -1;
    FILE* stream = NULL;
    int exitCode = -1;
    int devNull = -1;
    int savedStderr = -1;
    int savedErrno = 0;
    bool callbackSuccess = false;
    int childExitCode = 1;
    int waitpidStatus = -1;

    if (!logCommandLine("execCommand: cmdline=[%s]", argv)) {
        return -1;
    }

    if (pipe(pipefd) == -1) {
        JP_LOG_ERRNO;
        goto cleanup;
    }

    cpid = fork();
    if (cpid == -1) {
        JP_LOG_ERRNO;
    } else if (cpid == 0) /* Child process */ {
        /* Close unused read end */
        closePipeEnd(pipefd, 0);

        /* Save original stderr */
        if ((savedStderr = dup(STDERR_FILENO)) == -1) {
            JP_LOG_ERRNO;
            goto cleanupChild;
        }

        /* Redirect stdout of the child process into the pipe's end */
        if (dup2(pipefd[1], STDOUT_FILENO) == -1) {
            JP_LOG_ERRNO;
            goto cleanupChild;
        }

        devNull = open("/dev/null", O_WRONLY);
        if (devNull == -1) {
            JP_LOG_ERRNO;
            goto cleanupChild;
        }

        /* Silence stderr in the child process */
        if (dup2(devNull, STDERR_FILENO) == -1) {
            JP_LOG_ERRNO;
            goto cleanupChild;
        }

        childExitCode = 0;

cleanupChild:
        if (devNull != -1) {
            close(devNull);
        }

        closePipeEnd(pipefd, 0);

        if (childExitCode == 0) {
            execvp(argv[0], (char* const*)argv);

            /*
              Normally, execvp() doesn't return.
              If control flow reaches this point, execvp() failed.
              Restore stderr to make JP_LOG_ERRNO macro work and report error.
            */
            savedErrno = errno;
            dup2(savedStderr, STDERR_FILENO);
            errno = savedErrno;
            JP_LOG_ERRNO;

            close(savedStderr);

            childExitCode = 127; /* Command not found */
        }

        _exit(childExitCode);
    }

    /* Close unused write end */
    closePipeEnd(pipefd, 1);

    stream = fdopen(pipefd[0], "r");
    if (!stream) {
        JP_LOG_ERRNO;
        goto cleanup;
    }
    pipefd[0] = -1;

    callbackSuccess = invokeCallback(stream, callback, callbackData);
    if (!callbackSuccess) {
        JP_LOG_TRACE("Callback failed");
        goto cleanup;
    }

cleanup:
    if (stream) {
        fclose(stream);
    }

    closePipeEnd(pipefd, 0);
    closePipeEnd(pipefd, 1);

    if (cpid > 0) {
        while (waitpid(cpid, &waitpidStatus, 0) == -1 && errno == EINTR) {
        }

        if (WIFEXITED(waitpidStatus)) {
            exitCode = WEXITSTATUS(waitpidStatus);
        }
    }

    JP_LOG_TRACE("execCommand: exit=%d", exitCode);

    if (exitCode == 0 && !callbackSuccess) {
        exitCode = 1;
    }

    return exitCode;
}


void closePipeEnd(int* pipefd, int idx) {
    if (pipefd[idx] >= 0) {
        close(pipefd[idx]);
        pipefd[idx] = -1;
    }
}
