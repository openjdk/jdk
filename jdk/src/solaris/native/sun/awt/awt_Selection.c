/*
 * Copyright 1996-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "awt_p.h"
#include "awt_DataTransferer.h"
#include "java_awt_datatransfer_Transferable.h"
#include "java_awt_datatransfer_DataFlavor.h"
#include "sun_awt_motif_X11Selection.h"
#include "sun_awt_motif_X11Clipboard.h"
#include <X11/Intrinsic.h>
#include <X11/Xatom.h>
#include <inttypes.h>

#include <jni.h>
#include <jni_util.h>

/* fieldIDs for X11Selection fields that may be accessed from C */
static struct X11SelectionIDs {
    jfieldID holder;
    jfieldID atom;
    jfieldID contents;
    jfieldID selections;
} x11SelectionIDs;

DECLARE_JAVA_CLASS(selectionClazz, "sun/awt/motif/X11Selection")

static jobject
call_getSelectionsArray(JNIEnv* env) {
    DECLARE_STATIC_OBJECT_JAVA_METHOD(getSelectionsArray, selectionClazz,
                                    "getSelectionsArray", "()[Ljava/lang/Object;")
    DASSERT(!JNU_IsNull(env, getSelectionsArray));
    return (*env)->CallStaticObjectMethod(env, clazz, getSelectionsArray);
}

static void
call_checkChange(JNIEnv* env, jobject jselection, jlongArray targetArray)
{
    DECLARE_VOID_JAVA_METHOD(checkChangeMID, selectionClazz,
                             "checkChange", "([J)V")
    DASSERT(!JNU_IsNull(env, jselection));

    (*env)->CallVoidMethod(env, jselection, checkChangeMID, targetArray);
}

static jlongArray
call_getSelectionAtomsToCheckChange(JNIEnv* env)
{
    DECLARE_STATIC_OBJECT_JAVA_METHOD(getSelectionAtomsToCheckChangeMID,
            selectionClazz, "getSelectionAtomsToCheckChange", "()[J")

    return (jlongArray)(*env)->CallStaticObjectMethod(env,
            get_selectionClazz(env), getSelectionAtomsToCheckChangeMID);

}


/*
 * Class:     sun_awt_motif_X11Selection
 * Method:    initIDs
 * Signature: ()V
 */
/* This function gets called from the static initializer for
   X11Selection.java to initialize the fieldIDs for fields that may
   be accessed from C */
JNIEXPORT void JNICALL Java_sun_awt_motif_X11Selection_initIDs
    (JNIEnv *env, jclass cls)
{
    x11SelectionIDs.holder = (*env)->
        GetFieldID(env, cls, "holder","Lsun/awt/motif/X11SelectionHolder;");
    x11SelectionIDs.atom = (*env)->GetFieldID(env, cls, "atom", "J");
    x11SelectionIDs.contents = (*env)->
        GetFieldID(env, cls, "contents",
                   "Ljava/awt/datatransfer/Transferable;");
    x11SelectionIDs.selections = (*env)->
        GetStaticFieldID(env, cls, "selections", "Ljava/util/Vector;");
}

/*
 * Class:     sun_awt_motif_X11Selection
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_X11Selection_init
    (JNIEnv *env, jclass this)
{
    AWT_LOCK();

    AWT_UNLOCK();
}

static jobject
getX11Selection(JNIEnv * env, Atom atom)
{
    jobjectArray selections;
    jsize selectionCount, i;
    jobject selection;
    jobject returnSelection = NULL;

    selections = (jobjectArray)call_getSelectionsArray(env);

    if (JNU_IsNull(env, selections)) {
        return NULL;
    }

    selectionCount = (*env)->GetArrayLength(env, selections);

    for (i = 0; i < selectionCount; i++) {
        selection = (*env)->GetObjectArrayElement(env, selections, i);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
            break;
        }
        if (JNU_IsNull(env, selection)) {
            break;
        }
        if ((*env)->GetLongField(env, selection, x11SelectionIDs.atom) == atom) {
            returnSelection = selection;
        } else {
            (*env)->DeleteLocalRef(env, selection);
        }
    }

    (*env)->DeleteLocalRef(env, selections);

    return returnSelection;
}

Boolean
awtJNI_isSelectionOwner(JNIEnv * env, char *sel_str)
{
    Atom selection;
    jobject x11sel;

    selection = XInternAtom(awt_display, sel_str, False);

    x11sel = getX11Selection(env, selection);
    if (!JNU_IsNull(env, x11sel)) {
        jobject holder;

        holder = (*env)->GetObjectField(env, x11sel, x11SelectionIDs.holder);
        if (!JNU_IsNull(env, holder)) {
            return TRUE;
        }
    }
    return FALSE;
}

static void losingSelectionOwnership(Widget w, Atom * selection);

void
awtJNI_notifySelectionLost(JNIEnv * env, char *sel_str)
{
    Atom selection;

    selection = XInternAtom(awt_display, sel_str, False);
    losingSelectionOwnership(NULL, &selection);
}

static void
losingSelectionOwnership(Widget w, Atom * selection)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jobject this = getX11Selection(env, *selection);

    /*
     * SECURITY: OK to call this on privileged thread - peer does
     *         not call into client code
     */
    JNU_CallMethodByName(env, NULL, this, "lostSelectionOwnership", "()V");
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    /*
     * Fix for 4692059.
     * The native context is cleaned up on the event dispatch thread after the
     * references to the current contents and owner are cleared.
     */
}

/*
 * Class:     sun_awt_motif_X11Selection
 * Method:    pGetSelectionOwnership
 * Signature: (Ljava/lang/Object;Ljava/awt/datatransfer/Transferable;[JLjava/util/Map;Lsun/awt/motif/X11SelectionHolder;)Z
 */
JNIEXPORT jboolean JNICALL
Java_sun_awt_motif_X11Selection_pGetSelectionOwnership(JNIEnv *env,
                                                       jobject this,
                                                       jobject source,
                                                       jobject transferable,
                                                       jlongArray formats,
                                                       jobject formatMap,
                                                       jobject holder)
{
    Boolean gotit = False;
    Atom selection = (Atom)(*env)->GetLongField(env, this,
                                                x11SelectionIDs.atom);
    awt_convertDataCallbackStruct* structPtr = NULL;
    Time time = CurrentTime;

    AWT_LOCK();

    time = awt_util_getCurrentServerTime();

    (*env)->SetObjectField(env, this, x11SelectionIDs.holder, NULL);
    (*env)->SetObjectField(env, this, x11SelectionIDs.contents, NULL);

    gotit = XtOwnSelection(awt_root_shell, selection, time, awt_convertData,
                           losingSelectionOwnership, NULL);

    if (gotit) {
        if (XFindContext(awt_display, selection, awt_convertDataContext,
                         (XPointer*)&structPtr) == 0 && structPtr != NULL) {
            (*env)->DeleteGlobalRef(env, structPtr->source);
            (*env)->DeleteGlobalRef(env, structPtr->transferable);
            (*env)->DeleteGlobalRef(env, structPtr->formatMap);
            (*env)->DeleteGlobalRef(env, structPtr->formats);
            memset(structPtr, 0, sizeof(awt_convertDataCallbackStruct));
        } else {
            XDeleteContext(awt_display, selection, awt_convertDataContext);

            structPtr = calloc(1, sizeof(awt_convertDataCallbackStruct));

            if (structPtr == NULL) {
                XtDisownSelection(awt_root_shell, selection, time);
                AWT_UNLOCK();
                JNU_ThrowOutOfMemoryError(env, "");
                return JNI_FALSE;
            }

            if (XSaveContext(awt_display, selection, awt_convertDataContext,
                             (XPointer)structPtr) == XCNOMEM) {
                XtDisownSelection(awt_root_shell, selection, time);
                free(structPtr);
                AWT_UNLOCK();
                JNU_ThrowInternalError(env, "Failed to save context data for selection.");
                return JNI_FALSE;
            }
        }

        structPtr->source = (*env)->NewGlobalRef(env, source);
        structPtr->transferable = (*env)->NewGlobalRef(env, transferable);
        structPtr->formatMap = (*env)->NewGlobalRef(env, formatMap);
        structPtr->formats = (*env)->NewGlobalRef(env, formats);

        if (JNU_IsNull(env, structPtr->source) ||
            JNU_IsNull(env, structPtr->transferable) ||
            JNU_IsNull(env, structPtr->formatMap) ||
            JNU_IsNull(env, structPtr->formats)) {

            if (!JNU_IsNull(env, structPtr->source)) {
                (*env)->DeleteGlobalRef(env, structPtr->source);
            }
            if (!JNU_IsNull(env, structPtr->transferable)) {
                (*env)->DeleteGlobalRef(env, structPtr->transferable);
            }
            if (!JNU_IsNull(env, structPtr->formatMap)) {
                (*env)->DeleteGlobalRef(env, structPtr->formatMap);
            }
            if (!JNU_IsNull(env, structPtr->formats)) {
                (*env)->DeleteGlobalRef(env, structPtr->formats);
            }
            XtDisownSelection(awt_root_shell, selection, time);
            XDeleteContext(awt_display, selection, awt_convertDataContext);
            free(structPtr);
            AWT_UNLOCK();
            JNU_ThrowOutOfMemoryError(env, "");
            return JNI_FALSE;
        }

        (*env)->SetObjectField(env, this, x11SelectionIDs.holder, holder);
        (*env)->SetObjectField(env, this, x11SelectionIDs.contents, transferable);
    }
    AWT_UNLOCK();

    return (gotit ? JNI_TRUE : JNI_FALSE);
}

/*
 * Class:     sun_awt_motif_X11Selection
 * Method:    clearNativeContext
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_X11Selection_clearNativeContext(JNIEnv *env, jobject this) {
    Atom selection = (Atom)(*env)->GetLongField(env, this,
                                                x11SelectionIDs.atom);

    AWT_LOCK();

    XtDisownSelection(awt_root_shell, selection, CurrentTime);
    awt_cleanupConvertDataContext(env, selection);

    AWT_UNLOCK();
}


static void
getSelectionTargetsToCheckChange(Widget w, XtPointer client_data,
        Atom * selection, Atom * type, XtPointer value, unsigned long *length,
        int32_t *format)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    size_t count = 0, i = 0, j = 0;
    jlongArray targetArray = NULL;

    // Should keep this in sync with getSelectionTargets() so that
    // this function yields non-null targetArray iff
    // getSelectionTargets() yields SelectionSuccess.
    if (*type == XA_TARGETS || *type == XA_ATOM) {
        targetArray = getSelectionTargetsHelper(env, value, *length);
    } else if (*type != XT_CONVERT_FAIL) {
        targetArray = (*env)->NewLongArray(env, 0);
    }

    if (value != NULL) {
        XtFree(value);
        value = NULL;
    }

    {
        jobject jselection = getX11Selection(env, *selection);
        call_checkChange(env, jselection, targetArray);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
        (*env)->DeleteLocalRef(env, targetArray);
        (*env)->DeleteLocalRef(env, jselection);
    }
}


static Atom _XA_JAVA_TIME_PROPERTY_ATOM_CHECK_SELECTION_CHANGE_ON_TIMEOUT = 0;

static void
checkSelectionChangeOnTimeout(XtPointer client_data, XtIntervalId* id)
{
    // We don't call XtGetSelectionValue(..., TARGETS, ..., awt_util_getCurrentServerTime())
    // here because awt_util_getCurrentServerTime() may block toolkit therad for a while
    // whereas the current function is called very often at regular intervals.
    // Instead we call XtGetSelectionValue(..., XtLastTimestampProcessed(awt_display))
    // in the property change event handler wherein we have got an up-to-date timestamp.

    XChangeProperty(awt_display, XtWindow(awt_root_shell),
                    _XA_JAVA_TIME_PROPERTY_ATOM_CHECK_SELECTION_CHANGE_ON_TIMEOUT,
                    XA_ATOM, 32, PropModeAppend, (unsigned char *)"", 0);
    XFlush(awt_display);
}


static unsigned long selectionPollInterval;

static void
propertyChangeEventHandlerToSelectionCheck
(Widget w, XtPointer client_data, XEvent* event, Boolean* continue_to_dispatch)
{
    JNIEnv *env;
    jlongArray jselectionAtoms;

    if (event->type != PropertyNotify || event->xproperty.atom !=
            _XA_JAVA_TIME_PROPERTY_ATOM_CHECK_SELECTION_CHANGE_ON_TIMEOUT) {
        return;
    }

    env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    jselectionAtoms = call_getSelectionAtomsToCheckChange(env);

    DASSERT(!JNU_IsNull(env, jselectionAtoms));
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    } else {
        jsize len = (*env)->GetArrayLength(env, jselectionAtoms);
        jlong* selectionAtomsNative =
                (*env)->GetLongArrayElements(env, jselectionAtoms, NULL);
        if (!JNU_IsNull(env, selectionAtomsNative)) {
            jsize i = 0;
            for (i = 0; i < len; i++) {
                XtGetSelectionValue(awt_root_shell, (Atom)selectionAtomsNative[i], XA_TARGETS,
                                    getSelectionTargetsToCheckChange, (XtPointer)NULL,
                                    XtLastTimestampProcessed(awt_display));
            }
            (*env)->ReleaseLongArrayElements(env, jselectionAtoms,
                                             selectionAtomsNative, JNI_ABORT);
        }
    }

    // Reschedule the timer callback.
    XtAppAddTimeOut(awt_appContext, selectionPollInterval,
                    checkSelectionChangeOnTimeout, client_data);
}


static BOOL isClipboardViewerRegistered = FALSE;

/*
 * Class:     sun_awt_motif_X11Clipboard
 * Method:    registerClipboardViewer
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_X11Clipboard_registerClipboardViewer(JNIEnv *env, jobject self,
                                                        jint pollInterval)
{
    AWT_LOCK();

    if (isClipboardViewerRegistered) {
        AWT_UNLOCK();
        return;
    }

    if (_XA_JAVA_TIME_PROPERTY_ATOM_CHECK_SELECTION_CHANGE_ON_TIMEOUT == 0) {
        _XA_JAVA_TIME_PROPERTY_ATOM_CHECK_SELECTION_CHANGE_ON_TIMEOUT =
                XInternAtom(awt_display,
                            "_SUNW_JAVA_AWT_TIME_CHECK_SELECTION_CHANGE_ON_TIMEOUT",
                            False);
    }

    XtAddEventHandler(awt_root_shell, PropertyChangeMask, False,
                      propertyChangeEventHandlerToSelectionCheck, NULL);

    selectionPollInterval = pollInterval;

    XtAppAddTimeOut(awt_appContext, selectionPollInterval,
                    checkSelectionChangeOnTimeout, (XtPointer)NULL);

    isClipboardViewerRegistered = TRUE;

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_X11Clipboard
 * Method:    unregisterClipboardViewer
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_X11Clipboard_unregisterClipboardViewer(JNIEnv *env, jobject self)
{
    AWT_LOCK();

    if (!isClipboardViewerRegistered) {
        AWT_UNLOCK();
        return;
    }

    XtRemoveEventHandler(awt_root_shell, PropertyChangeMask, False,
                         propertyChangeEventHandlerToSelectionCheck, NULL);

    isClipboardViewerRegistered = FALSE;

    AWT_UNLOCK();
}


/*
 * Class:     sun_awt_motif_X11Clipboard
 * Method:    getClipboardFormats
 * Signature: (J)[J
 */
JNIEXPORT jlongArray JNICALL
Java_sun_awt_motif_X11Clipboard_getClipboardFormats
    (JNIEnv *env, jclass cls, jlong selectionAtom)
{
    Time time_stamp = awt_util_getCurrentServerTime();
    return get_selection_targets(env, selectionAtom, time_stamp);
}

/*
 * Class:     sun_awt_motif_X11Clipboard
 * Method:    getClipboardData
 * Signature: (JJ)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_sun_awt_motif_X11Clipboard_getClipboardData
    (JNIEnv *env, jclass cls, jlong selectionAtom, jlong format)
{
    Time time_stamp = awt_util_getCurrentServerTime();
    return get_selection_data(env, selectionAtom, format, time_stamp);
}
