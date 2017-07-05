/*
 * Copyright 1998-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

#ifdef HEADLESS
    #error This file should not be included in headless library
#endif

#include <Xm/Display.h>
#include "awt_Component.h"
#include "awt_Cursor.h"
#include "java_awt_Cursor.h"
#include <X11/cursorfont.h>

#include "jni.h"
#include "jni_util.h"

/* fieldIDs for Cursor fields that may be accessed from C */
struct CursorIDs cursorIDs;
extern struct MComponentPeerIDs mComponentPeerIDs;

static jweak curComp = 0;

/*
 * Class:     java_awt_Cursor
 * Method:    initIDs
 * Signature: ()V
 */
/*
 * This function gets called from the static initializer for Cursor.java
 * to initialize the fieldIDs for fields that may be accessed from C
 */
JNIEXPORT void JNICALL
Java_java_awt_Cursor_initIDs(JNIEnv *env, jclass cls)
{
    cursorIDs.type = (*env)->GetFieldID(env, cls, "type", "I");
    cursorIDs.mSetPData = (*env)->GetMethodID(env, cls, "setPData", "(J)V");
    cursorIDs.pData = (*env)->GetFieldID(env, cls, "pData", "J");
}

/*
 * A utility to retrieve cursor from java.awt.Cursor
 * Create and save the cursor first if it is not yet
 */
Cursor getCursor(JNIEnv *env, jobject jCur)
{
    int32_t cursorType = 0;
    Cursor  xcursor;

    xcursor = (Cursor)(*env)->GetLongField(env, jCur, cursorIDs.pData);

    if (xcursor != None) {
        return xcursor;
    }

    cursorType = (*env)->GetIntField(env, jCur, cursorIDs.type);

    DASSERT(cursorType != java_awt_Cursor_CUSTOM_CURSOR);

    switch (cursorType) {
    case java_awt_Cursor_DEFAULT_CURSOR:
        cursorType = XC_left_ptr;
        break;
    case java_awt_Cursor_CROSSHAIR_CURSOR:
        cursorType = XC_crosshair;
        break;
    case java_awt_Cursor_TEXT_CURSOR:
        cursorType = XC_xterm;
        break;
    case java_awt_Cursor_WAIT_CURSOR:
        cursorType = XC_watch;
        break;
    case java_awt_Cursor_SW_RESIZE_CURSOR:
        cursorType = XC_bottom_left_corner;
        break;
    case java_awt_Cursor_NW_RESIZE_CURSOR:
        cursorType = XC_top_left_corner;
        break;
    case java_awt_Cursor_SE_RESIZE_CURSOR:
        cursorType = XC_bottom_right_corner;
        break;
    case java_awt_Cursor_NE_RESIZE_CURSOR:
        cursorType = XC_top_right_corner;
        break;
    case java_awt_Cursor_S_RESIZE_CURSOR:
        cursorType = XC_bottom_side;
        break;
    case java_awt_Cursor_N_RESIZE_CURSOR:
        cursorType = XC_top_side;
        break;
    case java_awt_Cursor_W_RESIZE_CURSOR:
        cursorType = XC_left_side;
        break;
    case java_awt_Cursor_E_RESIZE_CURSOR:
        cursorType = XC_right_side;
        break;
    case java_awt_Cursor_HAND_CURSOR:
        cursorType = XC_hand2;
        break;
    case java_awt_Cursor_MOVE_CURSOR:
        cursorType = XC_fleur;
        break;
    }
    xcursor = XCreateFontCursor(awt_display, cursorType);

    (*env)->CallVoidMethod(env, jCur, cursorIDs.mSetPData, xcursor);
    return xcursor;
}

/*
 * Class:     java_awt_Cursor
 * Method:    finalizeImpl
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_awt_Cursor_finalizeImpl(JNIEnv *env, jclass clazz, jlong pData)
{
    Cursor xcursor;

    xcursor = (Cursor)pData;
    if (xcursor != None) {
        AWT_LOCK();
        XFreeCursor(awt_display, xcursor);
        AWT_UNLOCK();
    }
}

/*
 *  normal replace : CACHE_UDPATE  => update curComp and updateCursor
 *  not replace    : UPDATE_ONLY   => intact curComp but updateCursor
 *  only replace   : CACHE_ONLY    => update curComp only, not updateCursor
 *
 *  This function should only be called under AWT_LOCK(). Otherwise
 *  multithreaded access can corrupt the value of curComp variable.
 */
void updateCursor(XPointer client_data, int32_t replace) {

    static jclass globalCursorManagerClass = NULL;
    static jmethodID updateCursorID = NULL;

    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject peer = (jobject) client_data;
    jobject target;

    if ((*env)->PushLocalFrame(env, 16) < 0)
        return;

    target = (*env)->GetObjectField(env, peer, mComponentPeerIDs.target);
    if (replace != UPDATE_ONLY) {
        if (!JNU_IsNull(env, curComp)) {
            (*env)->DeleteWeakGlobalRef(env, curComp);
        }
        curComp = (*env)->NewWeakGlobalRef(env, target);
        if (replace == CACHE_ONLY) {
            (*env)->PopLocalFrame(env, 0);
            return;
        }
    }

    /* Initialize our java identifiers once. Checking before locking
     * is a huge performance win.
     */
    if (globalCursorManagerClass == NULL) {
        jobject sysClass = (*env)->FindClass(env, "sun/awt/motif/MGlobalCursorManager");
        if (sysClass != NULL) {
            /* Make this class 'sticky', we don't want it GC'd */
            globalCursorManagerClass = (*env)->NewGlobalRef(env, sysClass);

            updateCursorID = (*env)->GetStaticMethodID(env,
                                                       globalCursorManagerClass,
                                                       "nativeUpdateCursor",
                                                       "(Ljava/awt/Component;)V"
                                                       );
        }
        if (JNU_IsNull(env, globalCursorManagerClass) || updateCursorID == NULL) {
            JNU_ThrowClassNotFoundException(env, "sun/awt/motif/MGlobalCursorManager");
            (*env)->PopLocalFrame(env, 0);
            return;
        }
    } /* globalCursorManagerClass == NULL*/

    (*env)->CallStaticVoidMethod(env, globalCursorManagerClass,
                                 updateCursorID, target);
    DASSERT(!((*env)->ExceptionOccurred(env)));
    (*env)->PopLocalFrame(env, 0);
}

/*
 * Only call this function under AWT_LOCK(). Otherwise multithreaded
 * access can corrupt the value of curComp variable.
 */
jobject getCurComponent() {
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    return (*env)->NewLocalRef(env, curComp);
}
