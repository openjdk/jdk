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

#include <windows.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>

#include <jvm.h>

#include "jvm_symbols.h"

JvmSymbols* lookupJvmSymbols() {
    JvmSymbols* syms = (JvmSymbols*)malloc(sizeof(JvmSymbols));
    if (syms != NULL) {
        HINSTANCE jvm = GetModuleHandle("jvm.dll");
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
