/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef JDWP_VM_INTERFACE_H
#define JDWP_VM_INTERFACE_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <jni.h>
#include <jvm.h>
#include <jvmti.h>

#include "log_messages.h"

/* Macros that access interface functions */
#if !defined(lint)
    #define JVM_ENV_PTR(e,name)      (LOG_JVM(("%s()",#name)),  (e))
    #define JNI_ENV_PTR(e,name)      (LOG_JNI(("%s()",#name)),  (e))
    #define JVMTI_ENV_PTR(e,name)    (LOG_JVMTI(("%s()",#name)),(e))
#else
    #define JVM_ENV_PTR(e,name)      (e)
    #define JNI_ENV_PTR(e,name)      (e)
    #define JVMTI_ENV_PTR(e,name)    (e)
#endif

#define FUNC_PTR(e,name)       (*((*(e))->name))
#define JVM_FUNC_PTR(e,name)   FUNC_PTR(JVM_ENV_PTR  (e,name),name)
#define JNI_FUNC_PTR(e,name)   FUNC_PTR(JNI_ENV_PTR  (e,name),name)
#define JVMTI_FUNC_PTR(e,name) FUNC_PTR(JVMTI_ENV_PTR(e,name),name)

#endif
