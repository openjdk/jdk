/*
 * Copyright 1998-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "awt_KeyEvent.h"
#include "awt.h"

/************************************************************************
 * AwtKeyEvent fields
 */

jfieldID AwtKeyEvent::keyCodeID;
jfieldID AwtKeyEvent::keyCharID;
jfieldID AwtKeyEvent::rawCodeID;
jfieldID AwtKeyEvent::primaryLevelUnicodeID;
jfieldID AwtKeyEvent::scancodeID;
jfieldID AwtKeyEvent::extendedKeyCodeID;

/************************************************************************
 * AwtKeyEvent native methods
 */

extern "C" {

JNIEXPORT void JNICALL
Java_java_awt_event_KeyEvent_initIDs(JNIEnv *env, jclass cls) {
    TRY;

    AwtKeyEvent::keyCodeID = env->GetFieldID(cls, "keyCode", "I");
    AwtKeyEvent::keyCharID = env->GetFieldID(cls, "keyChar", "C");
    AwtKeyEvent::rawCodeID = env->GetFieldID(cls, "rawCode", "J");
    AwtKeyEvent::primaryLevelUnicodeID = env->GetFieldID(cls, "primaryLevelUnicode", "J");
    AwtKeyEvent::scancodeID = env->GetFieldID(cls, "scancode", "J");
    AwtKeyEvent::extendedKeyCodeID = env->GetFieldID(cls, "extendedKeyCode", "J");


    DASSERT(AwtKeyEvent::keyCodeID != NULL);
    DASSERT(AwtKeyEvent::keyCharID != NULL);
    DASSERT(AwtKeyEvent::rawCodeID != NULL);
    DASSERT(AwtKeyEvent::primaryLevelUnicodeID != NULL);
    DASSERT(AwtKeyEvent::scancodeID != NULL);
    DASSERT(AwtKeyEvent::extendedKeyCodeID != NULL);

    CATCH_BAD_ALLOC;
}

} /* extern "C" */
