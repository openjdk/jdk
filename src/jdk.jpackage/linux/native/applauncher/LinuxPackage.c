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

#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <linux/limits.h>
#include <unistd.h>
#include <libgen.h>
#include <stdbool.h>
#include "JvmLauncher.h"
#include "LinuxPackage.h"
#include "ExecCommand.h"


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
            return EXEC_CALLBACK_ERROR;
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
        JP_LOG_TRACE("JvmLauncherDesc(%s|%d|%s)",
                result->packageName ? result->packageName : "(null)",
                result->packageType,
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
