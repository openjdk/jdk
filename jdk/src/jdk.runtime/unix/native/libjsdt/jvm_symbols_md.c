/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
