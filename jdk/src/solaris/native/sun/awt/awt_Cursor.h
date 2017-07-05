/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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

#include "jni_util.h"
#include "awt_p.h"

#define CACHE_UPDATE 0          /* cache the component and update cursor */
#define UPDATE_ONLY  1          /* update cursor, but not cache component */
#define CACHE_ONLY   2          /* cache the component, no cursor update */

/* fieldIDs for Cursor fields that may be accessed from C */
struct CursorIDs {
  jfieldID type;
  jmethodID mSetPData;
  jfieldID pData;
};

#ifndef HEADLESS
Cursor getCursor(JNIEnv *env, jobject jCur);
void updateCursor(XPointer client_data, int32_t replace);
#endif /* !HEADLESS */
