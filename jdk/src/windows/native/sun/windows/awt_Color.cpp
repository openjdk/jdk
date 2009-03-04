/*
 * Copyright 1996-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "awt.h"
#include "awt_Color.h"


/************************************************************************
 * AwtColor fields
 */

jmethodID AwtColor::getRGBMID;


/************************************************************************
 * Color native methods
 */

extern "C" {

/*
 * Class:     java_awt_Color
 * Method:    initIDs
 * Signature: ()V;
 */
JNIEXPORT void JNICALL
Java_java_awt_Color_initIDs(JNIEnv *env, jclass cls)
{
    TRY;

    AwtColor::getRGBMID = env->GetMethodID(cls, "getRGB", "()I");
    DASSERT(AwtColor::getRGBMID != NULL);

    CATCH_BAD_ALLOC;
}

} /* extern "C" */

/************************************************************************
 * WColor native methods
 */

extern "C" {

/*
 * Class:     sun_awt_windows_WColor
 * Method:    getDefaultColor
 * Signature: (I)Ljava/awt/Color;
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_windows_WColor_getDefaultColor(JNIEnv *env, jclass cls,
                                            jint index)
{
    TRY;

    int iColor = 0;
    switch(index) {

    case sun_awt_windows_WColor_WINDOW_BKGND:
        iColor = COLOR_WINDOW;
        break;
    case sun_awt_windows_WColor_WINDOW_TEXT:
        iColor = COLOR_WINDOWTEXT;
        break;
    case sun_awt_windows_WColor_FRAME:
        iColor = COLOR_WINDOWFRAME;
        break;
    case sun_awt_windows_WColor_SCROLLBAR:
        iColor = COLOR_SCROLLBAR;
        break;
    case sun_awt_windows_WColor_MENU_BKGND:
        iColor = COLOR_MENU;
        break;
    case sun_awt_windows_WColor_MENU_TEXT:
        iColor = COLOR_MENUTEXT;
        break;
    case sun_awt_windows_WColor_BUTTON_BKGND:
        iColor = COLOR_BTNFACE;
        break;
    case sun_awt_windows_WColor_BUTTON_TEXT:
        iColor = COLOR_BTNTEXT;
        break;
    case sun_awt_windows_WColor_HIGHLIGHT:
        iColor = COLOR_HIGHLIGHT;
        break;

    default:
        return NULL;
    }
    DWORD c = ::GetSysColor(iColor);

    jobject wColor = JNU_NewObjectByName(env, "java/awt/Color", "(III)V",
                                         GetRValue(c), GetGValue(c),
                                         GetBValue(c));

    DASSERT(!safe_ExceptionOccurred(env));
    return wColor;

    CATCH_BAD_ALLOC_RET(NULL);
}

} /* extern "C" */
