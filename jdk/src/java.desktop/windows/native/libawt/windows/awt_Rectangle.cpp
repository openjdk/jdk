/*
 * Copyright (c) 1998, 1999, Oracle and/or its affiliates. All rights reserved.
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

#include "awt_Rectangle.h"
#include "awt.h"

/************************************************************************
 * AwtRectangle fields
 */

jfieldID AwtRectangle::xID;
jfieldID AwtRectangle::yID;
jfieldID AwtRectangle::widthID;
jfieldID AwtRectangle::heightID;

/************************************************************************
 * AwtRectangle native methods
 */

extern "C" {

JNIEXPORT void JNICALL
Java_java_awt_Rectangle_initIDs(JNIEnv *env, jclass cls) {
    TRY;

    AwtRectangle::xID = env->GetFieldID(cls, "x", "I");
    DASSERT(AwtRectangle::xID != NULL);
    CHECK_NULL(AwtRectangle::xID);

    AwtRectangle::yID = env->GetFieldID(cls, "y", "I");
    DASSERT(AwtRectangle::yID != NULL);
    CHECK_NULL(AwtRectangle::yID);

    AwtRectangle::widthID = env->GetFieldID(cls, "width", "I");
    DASSERT(AwtRectangle::widthID != NULL);
    CHECK_NULL(AwtRectangle::widthID);

    AwtRectangle::heightID = env->GetFieldID(cls, "height", "I");
    DASSERT(AwtRectangle::heightID != NULL);
    CHECK_NULL(AwtRectangle::heightID);

    CATCH_BAD_ALLOC;
}

} /* extern "C" */
