/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include <linux/limits.h>
#include <unistd.h>
#include <libgen.h>
#include <fcntl.h>
#include <stdbool.h>
#include <sys/wait.h>
#include "JvmLauncher.h"
#include "LinuxPackage.h"


static char* getModulePath(void) {
    char modulePath[PATH_MAX] = { 0 };
    ssize_t modulePathLen = 0;
    char* result = 0;

    modulePathLen = readlink("/proc/self/exe", modulePath,
                                                    sizeof(modulePath) - 1);
    if (modulePathLen < 0) {
        JP_LOG_ERRNO;
        return 0;
    }
    modulePath[modulePathLen] = '\0';
    result = strdup(modulePath);
    if (!result) {
        JP_LOG_ERRNO;
    }

    return result;
}


static void freePackageDesc(PackageDesc* desc) {
    if (desc) {
        free((void*)desc->name);
        free(desc);
    }
}


static PackageDesc* createPackageDesc(void) {
    PackageDesc* result = 0;

    result = malloc(sizeof(PackageDesc));
    if (!result) {
        JP_LOG_ERRNO;
        goto cleanup;
    }

    result->type = PACKAGE_TYPE_UNKNOWN;
    result->name = 0;

cleanup:
    return result;
}


static bool initPackageDesc(PackageDesc* desc, const char* str, int type) {
    char *newStr = strdup(str);
    if (!newStr) {
        JP_LOG_ERRNO;
        return false;
    }

    free((void*)desc->name);
    desc->name = newStr;
    desc->type = type;
    return true;
}


static JvmLauncherDesc* createJvmLauncherDesc(void) {
    JvmLauncherDesc* result = 0;

    result = malloc(sizeof(JvmLauncherDesc));
    if (!result) {
        JP_LOG_ERRNO;
        goto cleanup;
    }

    result->packageType = PACKAGE_TYPE_UNKNOWN;
    result->packageName = NULL;
    result->jvmLauncherLibPath = NULL;

cleanup:
    return result;
}


static bool initJvmLauncherDesc(
        JvmLauncherDesc*    desc,
        const PackageDesc*  packageDesc,
        const char*         jvmLauncherLibPath) {

    char* newJvmLauncherLibPath = NULL;
    char* newPackageName = NULL;

    newJvmLauncherLibPath = strdup(jvmLauncherLibPath);
    if (!newJvmLauncherLibPath) {
        JP_LOG_ERRNO;
        return false;
    }

    if (packageDesc && packageDesc->name) {
        newPackageName = strdup(packageDesc->name);
        if (!newPackageName) {
            JP_LOG_ERRNO;
            free(newJvmLauncherLibPath);
            return false;
        }
    }

    free((void*)desc->packageName);
    free((void*)desc->jvmLauncherLibPath);

    desc->packageName = newPackageName;
    if (packageDesc) {
        desc->packageType = packageDesc->type;
    } else {
        desc->packageType = PACKAGE_TYPE_UNKNOWN;
    }
    desc->jvmLauncherLibPath = newJvmLauncherLibPath;

    return true;
}


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

#define EXEC_CALLBACK_USE 1
#define EXEC_CALLBACK_IGNORE 0

typedef int (*execCallbackType)(void*, char*);

static bool invokeCallback(
        FILE* stream, execCallbackType callback, void* callbackData) {

    char* strBufBegin = 0;
    char* strBufEnd = 0;
    char* strBufNextChar = 0;
    char* strNewBufBegin = 0;
    size_t strBufCapacity = 0;
    int callbackMode = EXEC_CALLBACK_USE;
    int c;
    ptrdiff_t char_offset;
    int success = false;

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

    success = true;

cleanup:
    if (strBufBegin) {
        free(strBufBegin);
    }

    return success;
}

static int execCommand(
        const char* const   argv[],
        execCallbackType    callback,
        void*               callbackData) {

    int pipefd[] = { -1, -1 };
    pid_t cpid = -1;
    FILE* stream = NULL;
    int exitCode = -1;
    int devNull = -1;
    int savedStderr = -1;
    int savedErrno = 0;
    bool callbackSuccess = false;
    bool childReady = false;
    int waitpidStatus = -1;

    if (!logCommandLine("execCommand: cmdline: [%s]", argv)) {
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

        childReady = true;

cleanupChild:
        if (devNull != -1) {
            close(devNull);
        }

        closePipeEnd(pipefd, 0);

        if (childReady) {
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
        }

        _exit(127); /* Command not found */
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

    JP_LOG_TRACE("execCommand: child exit code: %d", exitCode);

    if (exitCode == 0 && !callbackSuccess) {
        exitCode = 1;
    }

    return exitCode;
}


static char* concat(const char *x, const char *y) {
    const size_t lenX = strlen(x);
    const size_t lenY = strlen(y);

    char *result = malloc(lenX + lenY + 1 /* \0 */);
    if (!result) {
        JP_LOG_ERRNO;
    } else {
        strcpy(result, x);
        strcat(result, y);
    }

    return result;
}


static int initRpmPackage(void* desc, char* str) {
    initPackageDesc((PackageDesc*)desc, str, PACKAGE_TYPE_RPM);
    return EXEC_CALLBACK_IGNORE;
}


static int initDebPackage(void* desc, char* str) {
    char* colonChrPos = strchr(str, ':');
    if (colonChrPos) {
        *colonChrPos = 0;
    }
    initPackageDesc((PackageDesc*)desc, str, PACKAGE_TYPE_DEB);
    return EXEC_CALLBACK_IGNORE;
}


#define LAUNCHER_LIB_NAME "/libapplauncher.so"

static int findLauncherLib(void* launcherLibPath, char* str) {
    char* buf = 0;
    const size_t strLen = strlen(str);
    const size_t launcherLibNameLen = strlen(LAUNCHER_LIB_NAME);

    if (launcherLibNameLen <= strLen
            && !strcmp(str + strLen - launcherLibNameLen, LAUNCHER_LIB_NAME)) {
        buf = strdup(str);
        if (!buf) {
            JP_LOG_ERRNO;
        } else {
            *(char**)launcherLibPath = buf;
        }
        return EXEC_CALLBACK_IGNORE;
    }
    return EXEC_CALLBACK_USE;
}


static PackageDesc* findOwnerOfFile(const char* path) {
    int execStatus = -1;
    PackageDesc* pkg = 0;
    const char* rpmOwner[] = {
        "rpm", "--queryformat", "%{NAME}", "-qf", path, NULL
    };
    const char* debOwner[] = {
        "dpkg", "-S", path, NULL
    };

    pkg = createPackageDesc();
    if (!pkg) {
        return 0;
    }

    execStatus = execCommand(rpmOwner, initRpmPackage, pkg);
    if (execStatus) {
        pkg->type = PACKAGE_TYPE_UNKNOWN;
        execStatus = execCommand(debOwner, initDebPackage, pkg);
    }

    if (execStatus) {
        pkg->type = PACKAGE_TYPE_UNKNOWN;
    }

    if (PACKAGE_TYPE_UNKNOWN == pkg->type || !pkg->name) {
        freePackageDesc(pkg);
        pkg = 0;
    }

    if (pkg) {
        JP_LOG_TRACE("owner pkg: (%s|%d)", pkg->name, pkg->type);
    }

    return pkg;
}


void freeJvmLauncherDesc(JvmLauncherDesc* desc) {
    if (desc) {
        free((void*)desc->packageName);
        free((void*)desc->jvmLauncherLibPath);
        free(desc);
    }
}


JvmLauncherDesc* getJvmLauncherDesc(void) {
    char* modulePath = 0;
    char* appImageDir = 0;
    char* launcherLibPath = 0;
    const char* pkgQueryCmd[] = {
        NULL, NULL, NULL, NULL
    };
    PackageDesc* pkg = 0;
    JvmLauncherDesc* result = 0;
    bool resultReady = false;

    modulePath = getModulePath();
    if (!modulePath) {
        goto cleanup;
    }

    pkg = findOwnerOfFile(modulePath);
    if (!pkg) {
        /* Not a package install */
        /* Launcher should be in "bin" subdirectory of app image. */
        /* Launcher lib should be in "lib" subdirectory of app image. */
        appImageDir = dirname(dirname(modulePath));
        launcherLibPath = concat(appImageDir, "/lib" LAUNCHER_LIB_NAME);
    } else {
        if (PACKAGE_TYPE_RPM == pkg->type) {
            pkgQueryCmd[0] = "rpm";
            pkgQueryCmd[1] = "-ql";
            pkgQueryCmd[2] = pkg->name;
        } else if (PACKAGE_TYPE_DEB == pkg->type) {
            pkgQueryCmd[0] = "dpkg";
            pkgQueryCmd[1] = "-L";
            pkgQueryCmd[2] = pkg->name;
        } else {
            /* Should never happen */
            JP_LOG_ERRMSG("Internal error");
            goto cleanup;
        }

        if (execCommand(pkgQueryCmd, findLauncherLib, &launcherLibPath)) {
            goto cleanup;
        }
    }

    if (!launcherLibPath) {
        goto cleanup;
    }

    result = createJvmLauncherDesc();
    if (result && initJvmLauncherDesc(result, pkg, launcherLibPath)) {
        JP_LOG_TRACE("JvmLauncherDesc(type=%d, name=%s, llib=%s)",
                result->packageType,
                result->packageName,
                result->jvmLauncherLibPath);
        resultReady = true;
    }

cleanup:
    free(modulePath);
    freePackageDesc(pkg);
    if (!resultReady) {
        freeJvmLauncherDesc(result);
        result = NULL;
    }
    free(launcherLibPath);

    return result;
}


void closePipeEnd(int* pipefd, int idx) {
    if (pipefd[idx] >= 0) {
        close(pipefd[idx]);
        pipefd[idx] = -1;
    }
}
