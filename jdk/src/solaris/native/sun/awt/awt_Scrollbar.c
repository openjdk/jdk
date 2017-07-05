/*
 * Copyright 1995-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "java_awt_Scrollbar.h"
#include "java_awt_event_MouseEvent.h"
#include "sun_awt_motif_MScrollbarPeer.h"
#include "sun_awt_motif_MComponentPeer.h"

#include "awt_Component.h"
#include "canvas.h"

#include <jni.h>
#include <jni_util.h>
#include "multi_font.h"


extern struct MComponentPeerIDs mComponentPeerIDs;
extern AwtGraphicsConfigDataPtr
    copyGraphicsConfigToPeer(JNIEnv *env, jobject this);

/* fieldIDs for java.awt.Scrollbar fields that may be accessed from C */
static struct ScrollbarIDs {
    jfieldID orientation;
    jfieldID visibleAmount;
    jfieldID lineIncrement;
    jfieldID pageIncrement;
    jfieldID value;
    jfieldID minimum;
    jfieldID maximum;
} targetIDs;

/* MScrollbarPeer callback methods */
static struct {
    jmethodID lineUp;
    jmethodID lineDown;
    jmethodID pageUp;
    jmethodID pageDown;
    jmethodID drag;
    jmethodID dragEnd;
    jmethodID warp;
} peerIDs;



/*
 * Class:     java_awt_ScrollBar
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for
   Scrollbar.java to initialize the fieldIDs for fields that may
   be accessed from C */

JNIEXPORT void JNICALL
Java_java_awt_Scrollbar_initIDs(JNIEnv *env, jclass cls)
{
    targetIDs.orientation =
      (*env)->GetFieldID(env, cls, "orientation", "I");
    targetIDs.visibleAmount =
      (*env)->GetFieldID(env, cls, "visibleAmount", "I");
    targetIDs.lineIncrement =
      (*env)->GetFieldID(env, cls, "lineIncrement", "I");
    targetIDs.pageIncrement =
      (*env)->GetFieldID(env, cls, "pageIncrement", "I");
    targetIDs.value =
      (*env)->GetFieldID(env, cls, "value", "I");
    targetIDs.minimum =
      (*env)->GetFieldID(env, cls, "minimum", "I");
    targetIDs.maximum =
      (*env)->GetFieldID(env, cls, "maximum", "I");
}


/*
 * Class:     sun_awt_motif_MComponentPeer
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for
   MScrollbarPeer to initialize the JNI ids for fields and methods
   that may be accessed from C */

JNIEXPORT void JNICALL
Java_sun_awt_motif_MScrollbarPeer_initIDs(JNIEnv *env, jclass cls)
{
    peerIDs.lineUp =
        (*env)->GetMethodID(env, cls, "lineUp",   "(I)V");
    peerIDs.lineDown =
        (*env)->GetMethodID(env, cls, "lineDown", "(I)V");
    peerIDs.pageUp =
        (*env)->GetMethodID(env, cls, "pageUp",   "(I)V");
    peerIDs.pageDown =
        (*env)->GetMethodID(env, cls, "pageDown", "(I)V");
    peerIDs.drag =
        (*env)->GetMethodID(env, cls, "drag",     "(I)V");
    peerIDs.dragEnd =
        (*env)->GetMethodID(env, cls, "dragEnd",  "(I)V");
    peerIDs.warp =
        (*env)->GetMethodID(env, cls, "warp",     "(I)V");
}

/*
 * Call peer.jcallback(value)
 */
static void
DoJavaCallback(jobject peer, jmethodID jcallback, jint value)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    (*env)->CallVoidMethod(env, peer, jcallback, value);
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}


static void /* XtCallbackProc */
decrementCallback(Widget w, jobject peer,
    XmScrollBarCallbackStruct *scroll)
{
    DASSERT(scroll->reason == XmCR_DECREMENT);
    DoJavaCallback(peer, peerIDs.lineUp, scroll->value);
}

static void /* XtCallbackProc */
incrementCallback(Widget w, jobject peer,
    XmScrollBarCallbackStruct *scroll)
{
    DASSERT(scroll->reason == XmCR_INCREMENT);
    DoJavaCallback(peer, peerIDs.lineDown, scroll->value);
}

static void /* XtCallbackProc */
pageDecrementCallback(Widget w, jobject peer,
    XmScrollBarCallbackStruct *scroll)
{
    DASSERT(scroll->reason == XmCR_PAGE_DECREMENT);
    DoJavaCallback(peer, peerIDs.pageUp, scroll->value);
}

static void /* XtCallbackProc */
pageIncrementCallback(Widget w, jobject peer,
    XmScrollBarCallbackStruct *scroll)
{
    DASSERT(scroll->reason == XmCR_PAGE_INCREMENT);
    DoJavaCallback(peer, peerIDs.pageDown, scroll->value);
}

static void /* XtCallbackProc */
dragCallback(Widget w, jobject peer,
    XmScrollBarCallbackStruct *scroll)
{
    DASSERT(scroll->reason == XmCR_DRAG);
    DoJavaCallback(peer, peerIDs.drag, scroll->value);
}

static void /* XtCallbackProc */
dragEndCallback(Widget w, jobject peer,
    XmScrollBarCallbackStruct *scroll)
{
    DASSERT(scroll->reason == XmCR_VALUE_CHANGED);
    DoJavaCallback(peer, peerIDs.dragEnd, scroll->value);
}

static void /* XtCallbackProc */
toTopCallback(Widget w, jobject peer,
    XmScrollBarCallbackStruct *scroll)
{
    DASSERT(scroll->reason == XmCR_TO_TOP);
    DoJavaCallback(peer, peerIDs.warp, scroll->value);
}

static void /* XtCallbackProc */
toBottomCallback(Widget w, jobject peer,
    XmScrollBarCallbackStruct *scroll)
{
    DASSERT(scroll->reason == XmCR_TO_BOTTOM);
    DoJavaCallback(peer, peerIDs.warp, scroll->value);
}


/*
 * Class:     sun_awt_motif_MScrollbarPeer
 * Method:    create
 * Signature: (Lsun/awt/motif/MComponentPeer;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MScrollbarPeer_create(JNIEnv *env, jobject this,
    jobject parent)
{
    Widget w;

    jobject target;
    XtPointer globalRef = (XtPointer) /* jobject */
        awtJNI_CreateAndSetGlobalRef(env, this);

    struct ComponentData *pdata; /* for parent     */
    struct ComponentData *sdata; /* for scrollbar */
    AwtGraphicsConfigDataPtr adata;

    int32_t value, visible, minimum, maximum;
    int32_t lineIncrement, pageIncrement;
    Pixel bg;

#define MAX_ARGC 20
    Arg args[MAX_ARGC];
    int32_t argc = 0;


    AWT_LOCK();

    if (JNU_IsNull(env, parent)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    pdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, parent, mComponentPeerIDs.pData);

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    if (JNU_IsNull(env, target) || pdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }


    switch ((*env)->GetIntField(env, target, targetIDs.orientation)) {
      case java_awt_Scrollbar_HORIZONTAL:
          XtSetArg(args[argc], XmNorientation, XmHORIZONTAL);
          argc++;
          break;

      case java_awt_Scrollbar_VERTICAL:
          XtSetArg(args[argc], XmNorientation, XmVERTICAL);
          argc++;
          break;

      default:
          JNU_ThrowIllegalArgumentException(env, "bad scrollbar orientation");
          AWT_UNLOCK();
          return;
    }

    adata = copyGraphicsConfigToPeer(env, this);
    XtVaGetValues(pdata->widget, XmNbackground, &bg, NULL);

    visible = (int32_t) (*env)->GetIntField(env, target, targetIDs.visibleAmount);
    value   = (int32_t) (*env)->GetIntField(env, target, targetIDs.value);
    minimum = (int32_t) (*env)->GetIntField(env, target, targetIDs.minimum);
    maximum = (int32_t) (*env)->GetIntField(env, target, targetIDs.maximum);
    lineIncrement =
              (int32_t) (*env)->GetIntField(env, target, targetIDs.lineIncrement);
    pageIncrement =
              (int32_t) (*env)->GetIntField(env, target, targetIDs.pageIncrement);

    /*
     * Sanity check.  Scrollbar.setValues should have taken care.
     */
    DASSERT(maximum > minimum);
    DASSERT(visible <= maximum - minimum);
    DASSERT(visible >= 1);
    DASSERT(value >= minimum);
    DASSERT(value <= maximum - visible);

    XtSetArg(args[argc], XmNx,             0);                  argc++;
    XtSetArg(args[argc], XmNy,             0);                  argc++;
    XtSetArg(args[argc], XmNvalue,         value);              argc++;
    XtSetArg(args[argc], XmNsliderSize,    visible);            argc++;
    XtSetArg(args[argc], XmNminimum,       minimum);            argc++;
    XtSetArg(args[argc], XmNmaximum,       maximum);            argc++;
    XtSetArg(args[argc], XmNincrement,     lineIncrement);      argc++;
    XtSetArg(args[argc], XmNpageIncrement, pageIncrement);      argc++;
    XtSetArg(args[argc], XmNbackground,    bg);                 argc++;
    XtSetArg(args[argc], XmNrecomputeSize, False);              argc++;
    XtSetArg(args[argc], XmNuserData,      globalRef);          argc++;
    XtSetArg(args[argc], XmNscreen,
                             ScreenOfDisplay(awt_display,
                                 adata->awt_visInfo.screen));   argc++;

    DASSERT(argc <= MAX_ARGC);  /* sanity check */

    sdata = ZALLOC(ComponentData);
    if (sdata == NULL) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();
        return;
    }

    JNU_SetLongFieldFromPtr(env, this, mComponentPeerIDs.pData, sdata);

    sdata->widget = w =
        XmCreateScrollBar(pdata->widget, "scrollbar", args, argc);

    XtAddCallback(w, XmNdecrementCallback,
        (XtCallbackProc)decrementCallback, globalRef);
    XtAddCallback(w, XmNincrementCallback,
        (XtCallbackProc)incrementCallback, globalRef);
    XtAddCallback(w, XmNpageDecrementCallback,
        (XtCallbackProc)pageDecrementCallback, globalRef);
    XtAddCallback(w, XmNpageIncrementCallback,
        (XtCallbackProc)pageIncrementCallback, globalRef);
    XtAddCallback(w, XmNtoTopCallback,
        (XtCallbackProc)toTopCallback, globalRef);
    XtAddCallback(w, XmNtoBottomCallback,
        (XtCallbackProc)toBottomCallback, globalRef);
    XtAddCallback(w, XmNdragCallback,
        (XtCallbackProc)dragCallback, globalRef);
    XtAddCallback(w, XmNvalueChangedCallback,
        (XtCallbackProc)dragEndCallback, globalRef);

    /* Set up workaround for the continuous scrolling bug */
    XtAddEventHandler(w, ButtonReleaseMask, False,
        awt_motif_Scrollbar_ButtonReleaseHandler, NULL);

    /* Fix for 4955950. ButtonRelease & MotionNotify should be handled as well */
    XtAddEventHandler(w, ButtonPressMask | ButtonReleaseMask | PointerMotionMask,
                      False, awt_canvas_event_handler, globalRef);

    XtSetMappedWhenManaged(w, False);
    XtManageChild(w);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MScrollbarPeer
 * Method:    pSetValues
 * Signature: (IIII)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MScrollbarPeer_pSetValues(JNIEnv *env, jobject this,
    jint value, jint visible, jint minimum, jint maximum)
{
    struct ComponentData *sdata;

    AWT_LOCK();

    sdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (sdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    /* pass in visible for sliderSize since Motif will calculate the */
    /* slider's size for us. */
    XtVaSetValues(sdata->widget,
                  XmNminimum, minimum,
                  XmNmaximum, maximum,
                  XmNvalue, value,
                  XmNsliderSize, visible,
                  NULL);
    AWT_FLUSH_UNLOCK();
}


/*
 * Class:     sun_awt_motif_MScrollbarPeer
 * Method:    setLineIncrement
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MScrollbarPeer_setLineIncrement(JNIEnv *env, jobject this,
    jint value)
{
    struct ComponentData *sdata;

    AWT_LOCK();

    sdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (sdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XtVaSetValues(sdata->widget,
                  XmNincrement, value,
                  NULL);
    AWT_FLUSH_UNLOCK();
}


/*
 * Class:     sun_awt_motif_MScrollbarPeer
 * Method:    setPageIncrement
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MScrollbarPeer_setPageIncrement(JNIEnv *env, jobject this,
    jint value)
{
    struct ComponentData *sdata;

    AWT_LOCK();

    sdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (sdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XtVaSetValues(sdata->widget,
                  XmNpageIncrement, value,
                  NULL);
    AWT_FLUSH_UNLOCK();
}
