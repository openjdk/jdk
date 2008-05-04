/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
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
