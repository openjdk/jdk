/*
 * Copyright (c) 2004, 2005, Oracle and/or its affiliates. All rights reserved.
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

/* Routines for various UTF conversions */

#ifndef  _UTF_H
#define _UTF_H

#include <stdio.h>

#include "jni.h"
#include "utf_md.h"

/* Error and assert macros */
#define UTF_ERROR(m) utfError(__FILE__, __LINE__,  m)
#define UTF_ASSERT(x) ( (x)==0 ? UTF_ERROR("ASSERT ERROR " #x) : (void)0 )

void utfError(char *file, int line, char *message);

struct UtfInst* JNICALL utfInitialize
                            (char *options);
void            JNICALL utfTerminate
                            (struct UtfInst *ui, char *options);
int             JNICALL utf8ToPlatform
                            (struct UtfInst *ui, jbyte *utf8,
                             int len, char *output, int outputMaxLen);
int             JNICALL utf8FromPlatform
                            (struct UtfInst *ui, char *str, int len,
                             jbyte *output, int outputMaxLen);
int             JNICALL utf8ToUtf16
                            (struct UtfInst *ui, jbyte *utf8, int len,
                             jchar *output, int outputMaxLen);
int             JNICALL utf16ToUtf8m
                            (struct UtfInst *ui, jchar *utf16, int len,
                             jbyte *output, int outputMaxLen);
int             JNICALL utf16ToUtf8s
                            (struct UtfInst *ui, jchar *utf16, int len,
                             jbyte *output, int outputMaxLen);
int             JNICALL utf8sToUtf8mLength
                            (struct UtfInst *ui, jbyte *string, int length);
void            JNICALL utf8sToUtf8m
                            (struct UtfInst *ui, jbyte *string, int length,
                             jbyte *new_string, int new_length);
int             JNICALL utf8mToUtf8sLength
                            (struct UtfInst *ui, jbyte *string, int length);
void            JNICALL utf8mToUtf8s
                            (struct UtfInst *ui, jbyte *string, int length,
                             jbyte *new_string, int new_length);

#endif
