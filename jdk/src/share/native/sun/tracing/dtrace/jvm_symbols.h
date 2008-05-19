/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

#ifndef _JVM_SYMBOLS_H
#define _JVM_SYMBOLS_H

#include "jvm.h"

typedef jint (JNICALL* GetVersion_t)(JNIEnv*);
typedef jboolean (JNICALL *IsSupported_t)(JNIEnv*);
typedef jlong (JNICALL* Activate_t)(
    JNIEnv*, jint, jstring, jint, JVM_DTraceProvider*);
typedef void (JNICALL *Dispose_t)(JNIEnv*, jlong);
typedef jboolean (JNICALL *IsProbeEnabled_t)(JNIEnv*, jmethodID);

typedef struct {
    GetVersion_t     GetVersion;
    IsSupported_t    IsSupported;
    Activate_t       Activate;
    Dispose_t        Dispose;
    IsProbeEnabled_t IsProbeEnabled;
} JvmSymbols;

// Platform-dependent implementation.
// Returns NULL if the symbols are not found
extern JvmSymbols* lookupJvmSymbols();

#endif // def _JVM_SYMBOLS_H
