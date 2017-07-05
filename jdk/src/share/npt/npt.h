/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

/* Native Platform Toolkit */

#ifndef  _NPT_H
#define _NPT_H

#define NPT_VERSION "0.0.0"

#include <stdio.h>

#include "jni.h"

#include "npt_md.h"
#include "utf.h"

#define NPT_ERROR(s) { (void)fprintf(stderr, "NPT ERROR: %s\n", s); exit(1); }

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {

    /* Used to save handle to our own dynamicly loaded library */
    void *libhandle;

    /* Copy of the options sent in at initialization */
    char *options;

    /* Can be used to save the UtfInst handle */
    struct UtfInst *utf;

    /* UTF interfaces, see utf.c */
    struct UtfInst* (JNICALL *utfInitialize)
                         (char *options);
    void     (JNICALL *utfTerminate)
                         (struct UtfInst *utf, char *options);
    int      (JNICALL *utf8ToPlatform)
                         (struct UtfInst *utf, jbyte *utf8, int len,
                          char *output, int outputMaxLen);
    int      (JNICALL *utf8FromPlatform)
                         (struct UtfInst *utf, char *str, int len,
                          jbyte *output, int outputMaxLen);
    int      (JNICALL *utf8ToUtf16)
                         (struct UtfInst *utf, jbyte *utf8, int len,
                          jchar *output, int outputMaxLen);
    int      (JNICALL *utf16ToUtf8m)
                         (struct UtfInst *utf, jchar *utf16, int len,
                          jbyte *output, int outputMaxLen);
    int      (JNICALL *utf16ToUtf8s)
                         (struct UtfInst *utf, jchar *utf16, int len,
                          jbyte *output, int outputMaxLen);
    int      (JNICALL *utf8sToUtf8mLength)
                         (struct UtfInst *utf, jbyte *string, int length);
    void     (JNICALL *utf8sToUtf8m)
                         (struct UtfInst *utf, jbyte *string, int length,
                          jbyte *newString, int newLength);
    int      (JNICALL *utf8mToUtf8sLength)
                         (struct UtfInst *utf, jbyte *string, int length);
    void     (JNICALL *utf8mToUtf8s)
                         (struct UtfInst *utf, jbyte *string, int length,
                          jbyte *newString, int newLength);

} NptEnv;

/* Typedefs for the only 2 'extern' functions in npt library:
 *    nptInitialize and nptTerminate
 *    See NPT_INITIALIZE() and NPT_TERMINATE() in npt_md.h.
 */

JNIEXPORT void JNICALL nptInitialize
                       (NptEnv **pnpt, char *nptVersion, char *options);
typedef JNIEXPORT void (JNICALL *NptInitialize)
                       (NptEnv **pnpt, char *nptVersion, char *options);

JNIEXPORT void JNICALL nptTerminate
                       (NptEnv* npt, char *options);
typedef JNIEXPORT void (JNICALL *NptTerminate)
                       (NptEnv* npt, char *options);

#ifdef __cplusplus
} /* extern "C" */
#endif /* __cplusplus */

#endif
