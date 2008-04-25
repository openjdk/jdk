/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

#include <windows.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>

#include <jvm.h>

#include "jvm_symbols.h"

JvmSymbols* lookupJvmSymbols() {
    JvmSymbols* syms = (JvmSymbols*)malloc(sizeof(JvmSymbols));
    if (syms != NULL) {
        HINSTANCE jvm = LoadLibrary("jvm.dll");
        if (jvm == NULL) {
            free(syms);
            return NULL;
        }
        syms->GetVersion = (GetVersion_t)
            GetProcAddress(jvm, "JVM_DTraceGetVersion");
        syms->IsSupported = (IsSupported_t)
            GetProcAddress(jvm, "JVM_DTraceIsSupported");
        syms->Activate = (Activate_t)
            GetProcAddress(jvm, "JVM_DTraceActivate");
        syms->Dispose = (Dispose_t)
            GetProcAddress(jvm, "JVM_DTraceDispose");
        syms->IsProbeEnabled = (IsProbeEnabled_t)
            GetProcAddress(jvm, "JVM_DTraceIsProbeEnabled");

        (void)FreeLibrary(jvm);
        if ( syms->GetVersion == NULL || syms->IsSupported == NULL ||
             syms->Activate == NULL || syms->Dispose == NULL ||
             syms->IsProbeEnabled == NULL) {
            free(syms);
            syms = NULL;
        }

    }
    return syms;
}
