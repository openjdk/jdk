/*
 * Copyright 1999-2001 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifndef _AWT_DRAWING_SURFACE_H_
#define _AWT_DRAWING_SURFACE_H_

#include <jawt.h>
#include <jni.h>
#include <jni_util.h>

_JNI_IMPORT_OR_EXPORT_ JAWT_DrawingSurface* JNICALL
    awt_GetDrawingSurface(JNIEnv* env, jobject target);

_JNI_IMPORT_OR_EXPORT_ void JNICALL
    awt_FreeDrawingSurface(JAWT_DrawingSurface* ds);

_JNI_IMPORT_OR_EXPORT_ void JNICALL
    awt_Lock(JNIEnv* env);

_JNI_IMPORT_OR_EXPORT_ void JNICALL
    awt_Unlock(JNIEnv* env);

_JNI_IMPORT_OR_EXPORT_ jobject JNICALL
    awt_GetComponent(JNIEnv* env, void* platformInfo);

#endif /* !_AWT_DRAWING_SURFACE_H_ */
