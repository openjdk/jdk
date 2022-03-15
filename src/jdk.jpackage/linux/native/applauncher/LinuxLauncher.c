/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#include <stdlib.h>
#include <dlfcn.h>
#include <errno.h>
#include <linux/limits.h>
#include <sys/wait.h>
#include <unistd.h>
#include <stddef.h>
#include <libgen.h>
#include "JvmLauncher.h"
#include "LinuxPackage.h"


#define STATUS_FAILURE 1

typedef JvmlLauncherHandle (*JvmlLauncherAPI_CreateFunType)(int argc, char *argv[]);

static int appArgc;
static char **appArgv;


static JvmlLauncherData* initJvmlLauncherData(int* size) {
    char* launcherLibPath = 0;
    void* jvmLauncherLibHandle = 0;
    JvmlLauncherAPI_GetAPIFunc getApi = 0;
    JvmlLauncherAPI_CreateFunType createJvmlLauncher = 0;
    JvmlLauncherAPI* api = 0;
    JvmlLauncherHandle jvmLauncherHandle = 0;
    JvmlLauncherData* result = 0;

    launcherLibPath = getJvmLauncherLibPath();
    if (!launcherLibPath) {
        goto cleanup;
    }

    jvmLauncherLibHandle = dlopen(launcherLibPath, RTLD_NOW | RTLD_LOCAL);
    if (!jvmLauncherLibHandle) {
        JP_LOG_ERRMSG(dlerror());
        goto cleanup;
    }

    getApi = dlsym(jvmLauncherLibHandle, "jvmLauncherGetAPI");
    if (!getApi) {
        JP_LOG_ERRMSG(dlerror());
        goto cleanup;
    }

    api = (*getApi)();
    if (!api) {
        JP_LOG_ERRMSG("Failed to get JvmlLauncherAPI instance");
        goto cleanup;
    }

    createJvmlLauncher = dlsym(jvmLauncherLibHandle, "jvmLauncherCreate");
    if (!createJvmlLauncher) {
        JP_LOG_ERRMSG(dlerror());
        goto cleanup;
    }

    jvmLauncherHandle = (*createJvmlLauncher)(appArgc, appArgv);
    if (!jvmLauncherHandle) {
        goto cleanup;
    }

    result = jvmLauncherCreateJvmlLauncherData(api, jvmLauncherHandle, size);
    /* Handle released in jvmLauncherCreateJvmlLauncherData() */
    jvmLauncherHandle = 0;

cleanup:
    if (jvmLauncherHandle) {
        jvmLauncherCloseHandle(api, jvmLauncherHandle);
    }
    if (jvmLauncherLibHandle) {
        dlclose(jvmLauncherLibHandle);
    }
    free(launcherLibPath);

    return result;
}


static int launchJvm(JvmlLauncherData* cfg) {
    void* jliLibHandle = 0;
    void* JLI_Launch;
    int exitCode = STATUS_FAILURE;

    jliLibHandle = dlopen(cfg->jliLibPath, RTLD_NOW | RTLD_LOCAL);
    if (!jliLibHandle) {
        JP_LOG_ERRMSG(dlerror());
        goto cleanup;
    }

    JLI_Launch = dlsym(jliLibHandle, "JLI_Launch");
    if (!JLI_Launch) {
        JP_LOG_ERRMSG(dlerror());
        goto cleanup;
    }

    exitCode = jvmLauncherStartJvm(cfg, JLI_Launch);

cleanup:
    if (jliLibHandle) {
        dlclose(jliLibHandle);
    }

    return exitCode;
}


static void closePipeEnd(int* pipefd, int idx) {
    if (pipefd[idx] >= 0) {
        close(pipefd[idx]);
        pipefd[idx] = -1;
    }
}


static void initJvmlLauncherDataPointers(void* baseAddress,
                                        JvmlLauncherData* jvmLauncherData) {
    ptrdiff_t offset = (char*)jvmLauncherData - (char*)baseAddress;
    int i;

    jvmLauncherData->jliLibPath += offset;
    jvmLauncherData->jliLaunchArgv =
            (char**)((char*)jvmLauncherData->jliLaunchArgv + offset);
    jvmLauncherData->envVarNames =
            (char**)((char*)jvmLauncherData->envVarNames + offset);
    jvmLauncherData->envVarValues =
            (char**)((char*)jvmLauncherData->envVarValues + offset);

    for (i = 0; i != jvmLauncherData->jliLaunchArgc; i++) {
        jvmLauncherData->jliLaunchArgv[i] += offset;
    }

    for (i = 0; i != jvmLauncherData->envVarCount; i++) {
        jvmLauncherData->envVarNames[i] += offset;
        jvmLauncherData->envVarValues[i] += offset;
    }
}


int main(int argc, char *argv[]) {
    int pipefd[2];
    pid_t cpid;
    int exitCode = STATUS_FAILURE;
    JvmlLauncherData* jvmLauncherData = 0;
    int jvmLauncherDataBufferSize = 0;

    appArgc = argc;
    appArgv = argv;

    if (pipe(pipefd) == -1) {
        JP_LOG_ERRNO;
        return exitCode;
    }

    cpid = fork();
    if (cpid == -1) {
        JP_LOG_ERRNO;
    } else if (cpid == 0) /* Child process */ {
        /* Close unused read end */
        closePipeEnd(pipefd, 0);

        jvmLauncherData = initJvmlLauncherData(&jvmLauncherDataBufferSize);
        if (jvmLauncherData) {
            /* Buffer size */
            if (write(pipefd[1], &jvmLauncherDataBufferSize,
                                    sizeof(jvmLauncherDataBufferSize)) == -1) {
                JP_LOG_ERRNO;
                goto cleanup;
            }
            if (jvmLauncherDataBufferSize) {
                /* Buffer address */
                if (write(pipefd[1], &jvmLauncherData,
                                            sizeof(jvmLauncherData)) == -1) {
                    JP_LOG_ERRNO;
                    goto cleanup;
                }
                /* Buffer data */
                if (write(pipefd[1], jvmLauncherData,
                                            jvmLauncherDataBufferSize) == -1) {
                    JP_LOG_ERRNO;
                    goto cleanup;
                }
            }
        }

        exitCode = 0;
    } else if (cpid > 0) {
        void* baseAddress = 0;

        /* Close unused write end */
        closePipeEnd(pipefd, 1);

        if (read(pipefd[0], &jvmLauncherDataBufferSize,
                                sizeof(jvmLauncherDataBufferSize)) == -1) {
            JP_LOG_ERRNO;
            goto cleanup;
        }

        if (jvmLauncherDataBufferSize == 0) {
            JP_LOG_ERRNO;
            goto cleanup;
        }

        if (read(pipefd[0], &baseAddress, sizeof(baseAddress)) == -1) {
            JP_LOG_ERRNO;
            goto cleanup;
        }

        jvmLauncherData = malloc(jvmLauncherDataBufferSize);
        if (!jvmLauncherData) {
            JP_LOG_ERRNO;
            goto cleanup;
        }

        if (read(pipefd[0], jvmLauncherData,
                                        jvmLauncherDataBufferSize) == -1) {
            JP_LOG_ERRNO;
            goto cleanup;
        }

        closePipeEnd(pipefd, 0);
        wait(NULL); /* Wait child process to terminate */

        initJvmlLauncherDataPointers(baseAddress, jvmLauncherData);
        exitCode = launchJvm(jvmLauncherData);
    }

cleanup:
    closePipeEnd(pipefd, 0);
    closePipeEnd(pipefd, 1);
    free(jvmLauncherData);
    return exitCode;
}
