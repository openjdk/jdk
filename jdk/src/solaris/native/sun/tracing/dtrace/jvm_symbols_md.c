/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

#include <dlfcn.h>
#include <stdlib.h>

#include <jvm.h>

#include "jvm_symbols.h"

JvmSymbols* lookupJvmSymbols() {
    JvmSymbols* syms = (JvmSymbols*)malloc(sizeof(JvmSymbols));
    if (syms != NULL) {
        syms->GetVersion = (GetVersion_t)
            dlsym(RTLD_DEFAULT, "JVM_DTraceGetVersion");
        syms->IsSupported = (IsSupported_t)
            dlsym(RTLD_DEFAULT, "JVM_DTraceIsSupported");
        syms->Activate = (Activate_t)
            dlsym(RTLD_DEFAULT, "JVM_DTraceActivate");
        syms->Dispose = (Dispose_t)
            dlsym(RTLD_DEFAULT, "JVM_DTraceDispose");
        syms->IsProbeEnabled = (IsProbeEnabled_t)
            dlsym(RTLD_DEFAULT, "JVM_DTraceIsProbeEnabled");

        if ( syms->GetVersion == NULL || syms->Activate == NULL ||
             syms->IsProbeEnabled == NULL || syms->Dispose == NULL ||
             syms->IsSupported == NULL) {
            free(syms);
            syms = NULL;
        }
    }
    return syms;
}
