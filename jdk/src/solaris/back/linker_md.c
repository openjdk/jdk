/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Adapted from JDK 1.2 linker_md.c v1.37. Note that we #define
 * NATIVE here, whether or not we're running solaris native threads.
 * Outside the VM, it's unclear how we can do the locking that is
 * done in the green threads version of the code below.
 */
#define NATIVE

/*
 * Machine Dependent implementation of the dynamic linking support
 * for java.  This routine is Solaris specific.
 */

#include <stdio.h>
#include <dlfcn.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>

#include "path_md.h"
#ifndef NATIVE
#include "iomgr.h"
#include "threads_md.h"
#endif

#ifdef __APPLE__
#define LIB_SUFFIX "dylib"
#else
#define LIB_SUFFIX "so"
#endif

/*
 * create a string for the JNI native function name by adding the
 * appropriate decorations.
 */
int
dbgsysBuildFunName(char *name, int nameLen, int args_size, int encodingIndex)
{
  /* On Solaris, there is only one encoding method. */
    if (encodingIndex == 0)
        return 1;
    return 0;
}

/*
 * create a string for the dynamic lib open call by adding the
 * appropriate pre and extensions to a filename and the path
 */
void
dbgsysBuildLibName(char *holder, int holderlen, char *pname, char *fname)
{
    const int pnamelen = pname ? strlen(pname) : 0;

    /* Quietly truncate on buffer overflow.  Should be an error. */
    if (pnamelen + (int)strlen(fname) + 10 > holderlen) {
        *holder = '\0';
        return;
    }

    if (pnamelen == 0) {
        (void)snprintf(holder, holderlen, "lib%s." LIB_SUFFIX, fname);
    } else {
        (void)snprintf(holder, holderlen, "%s/lib%s." LIB_SUFFIX, pname, fname);
    }
}

#ifndef NATIVE
extern int thr_main(void);
#endif

void *
dbgsysLoadLibrary(const char *name, char *err_buf, int err_buflen)
{
    void * result;
#ifdef NATIVE
    result = dlopen(name, RTLD_LAZY);
#else
    sysMonitorEnter(greenThreadSelf(), &_dl_lock);
    result = dlopen(name, RTLD_NOW);
    sysMonitorExit(greenThreadSelf(), &_dl_lock);
    /*
     * This is a bit of bulletproofing to catch the commonly occurring
     * problem of people loading a library which depends on libthread into
     * the VM.  thr_main() should always return -1 which means that libthread
     * isn't loaded.
     */
    if (thr_main() != -1) {
         VM_CALL(panic)("libthread loaded into green threads");
    }
#endif
    if (result == NULL) {
        (void)strncpy(err_buf, dlerror(), err_buflen-2);
        err_buf[err_buflen-1] = '\0';
    }
    return result;
}

void dbgsysUnloadLibrary(void *handle)
{
#ifndef NATIVE
    sysMonitorEnter(greenThreadSelf(), &_dl_lock);
#endif
    (void)dlclose(handle);
#ifndef NATIVE
    sysMonitorExit(greenThreadSelf(), &_dl_lock);
#endif
}

void * dbgsysFindLibraryEntry(void *handle, const char *name)
{
    void * sym;
#ifndef NATIVE
    sysMonitorEnter(greenThreadSelf(), &_dl_lock);
#endif
    sym =  dlsym(handle, name);
#ifndef NATIVE
    sysMonitorExit(greenThreadSelf(), &_dl_lock);
#endif
    return sym;
}
