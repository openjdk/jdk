/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

#include <jni.h>

#include "sun_security_pkcs11_Secmod.h"

// #define SECMOD_DEBUG

#include "j2secmod_md.h"

#include "p11_md.h"


void *findFunction(JNIEnv *env, jlong jHandle, const char *functionName);

#ifdef SECMOD_DEBUG
#define dprintf(s) printf(s)
#define dprintf1(s, p1) printf(s, p1)
#define dprintf2(s, p1, p2) printf(s, p1, p2)
#define dprintf3(s, p1, p2, p3) printf(s, p1, p2, p3)
#else
#define dprintf(s)
#define dprintf1(s, p1)
#define dprintf2(s, p1, p2)
#define dprintf3(s, p1, p2, p3)
#endif

// NSS types

typedef int PRBool;

typedef struct SECMODModuleStr SECMODModule;
typedef struct SECMODModuleListStr SECMODModuleList;

struct SECMODModuleStr {
    void        *v1;
    PRBool      internal;       /* true of internally linked modules, false
                                 * for the loaded modules */
    PRBool      loaded;         /* Set to true if module has been loaded */
    PRBool      isFIPS;         /* Set to true if module is finst internal */
    char        *dllName;       /* name of the shared library which implements
                                 * this module */
    char        *commonName;    /* name of the module to display to the user */
    void        *library;       /* pointer to the library. opaque. used only by
                                 * pk11load.c */

    void        *functionList; /* The PKCS #11 function table */
    void        *refLock;       /* only used pk11db.c */
    int         refCount;       /* Module reference count */
    void        **slots;        /* array of slot points attached to this mod*/
    int         slotCount;      /* count of slot in above array */
    void        *slotInfo;      /* special info about slots default settings */
    int         slotInfoCount;  /* count */
    // incomplete, sizeof() is wrong
};

struct SECMODModuleListStr {
    SECMODModuleList    *next;
    SECMODModule        *module;
};
