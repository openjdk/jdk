/*
 * Copyright 1996-2002 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "java_awt_Adjustable.h"
#include "java_awt_Insets.h"
#include "java_awt_ScrollPane.h"
#include "java_awt_event_AdjustmentEvent.h"
#include "sun_awt_motif_MComponentPeer.h"
#include "sun_awt_motif_MScrollPanePeer.h"
#include "java_awt_AWTEvent.h"

#include "awt_Component.h"
#include "canvas.h"

#include <jni.h>
#include <jni_util.h>
#include <Xm/ScrolledWP.h>

extern struct MComponentPeerIDs mComponentPeerIDs;
extern AwtGraphicsConfigDataPtr
    copyGraphicsConfigToPeer(JNIEnv *env, jobject this);

/* fieldIDs for ScrollPane fields that may be accessed from C */
static struct ScrollPaneIDs {
    jfieldID scrollbarDisplayPolicy;
} scrollPaneIDs;

/*
 * Class:     java_awt_ScrollPane
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for
   ScrollPane.java to initialize the fieldIDs for fields that may
   be accessed from C */

JNIEXPORT void JNICALL Java_java_awt_ScrollPane_initIDs
  (JNIEnv *env, jclass cls)
{
    scrollPaneIDs.scrollbarDisplayPolicy =
      (*env)->GetFieldID(env, cls, "scrollbarDisplayPolicy", "I");
}

/* fieldIDs for MScrollPanePeer fields that may be accessed from C */
static struct MScrollPanePeerIDs {
    jmethodID postScrollEventID;
} mScrollPanePeerIDs;

/*
 * Class:     sun_awt_motif_MScrollPanePeer
 * Method:    initIDs
 * Signature: ()V
 */

/* This function gets called from the static initializer for
   MScrollPanePeer.java to initialize the fieldIDs for fields that may
   be accessed from C */

JNIEXPORT void JNICALL Java_sun_awt_motif_MScrollPanePeer_initIDs
  (JNIEnv *env, jclass cls)
{
    mScrollPanePeerIDs.postScrollEventID =
        (*env)->GetMethodID(env, cls, "postScrollEvent", "(IIIZ)V");
}

static void
dump_scroll_attrs(Widget scrollbar)
{
    unsigned char orient;
    int32_t value, size, incr, pIncr, max, min;

    XtVaGetValues(scrollbar,
                  XmNvalue, &value,
                  XmNincrement, &incr,
                  XmNpageIncrement, &pIncr,
                  XmNsliderSize, &size,
                  XmNmaximum, &max,
                  XmNminimum, &min,
                  XmNorientation, &orient,
                  NULL);

    jio_fprintf(stdout, "%s: min=%d max=%d slider-size=%d incr=%d pageIncr=%d value = %d\n",
                orient == XmVERTICAL ? "VSB" : "HSB", min, max, size,
                incr, pIncr, value);
}


/*
 * client_data is MScrollPanePeer instance
 */
static void
postScrollEvent(jint jorient, jobject peer, XmScrollBarCallbackStruct *scroll)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);

    jint jscrollcode;
    jboolean jadjusting = JNI_FALSE;

    switch (scroll->reason) {
      case XmCR_DECREMENT:
          jscrollcode = java_awt_event_AdjustmentEvent_UNIT_DECREMENT;
          break;
      case XmCR_INCREMENT:
          jscrollcode = java_awt_event_AdjustmentEvent_UNIT_INCREMENT;
          break;
      case XmCR_PAGE_DECREMENT:
          jscrollcode = java_awt_event_AdjustmentEvent_BLOCK_DECREMENT;
          break;
      case XmCR_PAGE_INCREMENT:
          jscrollcode = java_awt_event_AdjustmentEvent_BLOCK_INCREMENT;
          break;
      case XmCR_DRAG:
          jscrollcode = java_awt_event_AdjustmentEvent_TRACK;
          jadjusting = JNI_TRUE;
          break;
      case XmCR_VALUE_CHANGED:  /* drag finished */
      case XmCR_TO_TOP:
      case XmCR_TO_BOTTOM:
          jscrollcode = java_awt_event_AdjustmentEvent_TRACK;
          break;
      default:
          DASSERT(FALSE);
          return;
    }

    (*env)->CallVoidMethod(env, peer,  mScrollPanePeerIDs.postScrollEventID,
        jorient, jscrollcode, (jint)scroll->value, jadjusting);

    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
}

/*
 * client_data is MScrollPanePeer instance
 */
static void
ScrollPane_scrollV(Widget w, XtPointer client_data, XtPointer call_data)
{
    postScrollEvent(java_awt_Adjustable_VERTICAL, (jobject)client_data,
                    (XmScrollBarCallbackStruct *)call_data);
}

/*
 * client_data is MScrollPanePeer instance
 */
static void
ScrollPane_scrollH(Widget w, XtPointer client_data, XtPointer call_data)
{
    postScrollEvent(java_awt_Adjustable_HORIZONTAL, (jobject)client_data,
                    (XmScrollBarCallbackStruct *)call_data);
}


typedef XmNavigability (*NavigableCallback) (Widget);

NavigableCallback oldClipNavigable = NULL;
Boolean clipCallbackInitialized = False;
XmNavigability MyClipNavigable(Widget wid) {
    // We've installed this function for ClipWindow
    if (XmIsClipWindow(wid)) {
        // To be able to request focus on ClipWindow by call
        // XmProcessTraversal(, XmTRAVERSE_CURRENT) we need to make
        // it return XmCONTROL_NAVIGABLE. Default implementation returns
        // DESCENDANTS_TAB_NAVIGABLE which doesn't allow this.
        return XmCONTROL_NAVIGABLE;
    }
    if (oldClipNavigable) {
        return oldClipNavigable(wid);
    }
    // this will never happen
    return XmCONTROL_NAVIGABLE;
}

const char * ScrollPaneManagerName = "ScrolledWindowClipWindow";
NavigableCallback oldManagerNavigable = NULL;
Boolean managerCallbackInitialized = False;
XmNavigability MyManagerNavigable(Widget wid) {
    // We've installed this function for Manager
    // with the name ScrollPaneManagerName
    if (XmIsManager(wid)
        && ( XtName(wid) != NULL && strcmp(XtName(wid), ScrollPaneManagerName) == 0) )
    {
        // To be able to request focus on Manager by call
        // XmProcessTraversal(, XmTRAVERSE_CURRENT) we need to make
        // it return XmCONTROL_NAVIGABLE. Default implementation returns
        // DESCENDANTS_TAB_NAVIGABLE which doesn't allow this.
        return XmCONTROL_NAVIGABLE;
    }
    if (oldManagerNavigable) {
        return oldManagerNavigable(wid);
    }
    // this will never happen
    return XmCONTROL_NAVIGABLE;
}

/*
 * Class:     sun_awt_motif_MScrollPanePeer
 * Method:    create
 * Signature: (Lsun/awt/motif/MComponentPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MScrollPanePeer_create
  (JNIEnv *env, jobject this, jobject parent)
{
    int32_t argc;
#define MAX_ARGC 40
    Arg args[MAX_ARGC];
    struct ComponentData *wdata;
    struct ComponentData *sdata;
    jobject target;
    Pixel bg;
    Widget vsb, hsb;
    jint sbDisplay;
    jobject globalRef = awtJNI_CreateAndSetGlobalRef(env, this);
    AwtGraphicsConfigDataPtr adata;

    AWT_LOCK();

    if (JNU_IsNull(env, parent)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();

        return;
    }
    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);
    wdata = (struct ComponentData *)
      JNU_GetLongFieldAsPtr(env,parent,mComponentPeerIDs.pData);

    if (JNU_IsNull(env, target) || wdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();

        return;
    }
    sdata = ZALLOC(ComponentData);
    JNU_SetLongFieldFromPtr(env,this,mComponentPeerIDs.pData,sdata);

    if (sdata == NULL) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();
        return;
    }
    XtVaGetValues(wdata->widget, XmNbackground, &bg, NULL);

    adata = copyGraphicsConfigToPeer(env, this);

    argc = 0;

    sbDisplay =
      (*env)->GetIntField(env, target, scrollPaneIDs.scrollbarDisplayPolicy);

    XtSetArg(args[argc], XmNuserData, (XtPointer) globalRef);
    argc++;


    if (sbDisplay == java_awt_ScrollPane_SCROLLBARS_NEVER) {
        DASSERT(!(argc > MAX_ARGC));
        sdata->widget = XtCreateWidget(ScrollPaneManagerName,
                                       xmManagerWidgetClass, wdata->widget,
                                       args, argc);

        {
            // To be able to request focus on Manager by call
            // XmProcessTraversal(, XmTRAVERSE_CURRENT) we need to make
            // it return XmCONTROL_NAVIGABLE from widgetNavigable callback.
            // Default implementation returns DESCENDANTS_TAB_NAVIGABLE
            // which doesn't allow this.
            if (!managerCallbackInitialized) {
                XmBaseClassExt *er;
                WidgetClass wc;
                managerCallbackInitialized = True;
                wc = (WidgetClass) &xmManagerClassRec;
                er = _XmGetBaseClassExtPtr(wc, XmQmotif);
                oldManagerNavigable = (*er)->widgetNavigable;
                (*er)->widgetNavigable = MyManagerNavigable;
            }
        }
    }
    else
    {
        XtSetArg(args[argc], XmNscrollingPolicy, XmAUTOMATIC);
        argc++;
        XtSetArg(args[argc], XmNvisualPolicy, XmCONSTANT);
        argc++;
        if (sbDisplay == java_awt_ScrollPane_SCROLLBARS_ALWAYS) {
            DASSERT(!(argc > MAX_ARGC));
            XtSetArg(args[argc], XmNscrollBarDisplayPolicy, XmSTATIC);
            argc++;
        } else {
            XtSetArg(args[argc], XmNscrollBarDisplayPolicy, XmAS_NEEDED);
            argc++;
        }

        XtSetArg(args[argc], XmNspacing, 0);
        argc++;
        XtSetArg (args[argc], XmNscreen,
                  ScreenOfDisplay(awt_display,
                                  adata->awt_visInfo.screen));
        argc++;

        DASSERT(!(argc > MAX_ARGC));
        sdata->widget = XmCreateScrolledWindow(wdata->widget, "scroller", args, argc);

        XtVaGetValues(sdata->widget,
                      XmNverticalScrollBar, &vsb,
                      XmNhorizontalScrollBar, &hsb,
                      NULL);

        if (vsb != NULL) {
            XtAddCallback(vsb, XmNincrementCallback, ScrollPane_scrollV, (XtPointer) globalRef);
            XtAddCallback(vsb, XmNdecrementCallback, ScrollPane_scrollV, (XtPointer) globalRef);
            XtAddCallback(vsb, XmNpageIncrementCallback, ScrollPane_scrollV, (XtPointer) globalRef);
            XtAddCallback(vsb, XmNpageDecrementCallback, ScrollPane_scrollV, (XtPointer) globalRef);
            XtAddCallback(vsb, XmNtoTopCallback, ScrollPane_scrollV, (XtPointer) globalRef);
            XtAddCallback(vsb, XmNtoBottomCallback, ScrollPane_scrollV, (XtPointer) globalRef);
            XtAddCallback(vsb, XmNvalueChangedCallback, ScrollPane_scrollV, (XtPointer) globalRef);
            XtAddCallback(vsb, XmNdragCallback, ScrollPane_scrollV, (XtPointer) globalRef);

            XtVaSetValues(vsb, XmNhighlightThickness, 0, NULL);
        }
        if (hsb != NULL) {
            XtAddCallback(hsb, XmNincrementCallback, ScrollPane_scrollH, (XtPointer) globalRef);
            XtAddCallback(hsb, XmNdecrementCallback, ScrollPane_scrollH, (XtPointer) globalRef);
            XtAddCallback(hsb, XmNpageIncrementCallback, ScrollPane_scrollH, (XtPointer) globalRef);
            XtAddCallback(hsb, XmNpageDecrementCallback, ScrollPane_scrollH, (XtPointer) globalRef);
            XtAddCallback(hsb, XmNtoTopCallback, ScrollPane_scrollH, (XtPointer) globalRef);
            XtAddCallback(hsb, XmNtoBottomCallback, ScrollPane_scrollH, (XtPointer) globalRef);
            XtAddCallback(hsb, XmNvalueChangedCallback, ScrollPane_scrollH, (XtPointer) globalRef);
            XtAddCallback(hsb, XmNdragCallback, ScrollPane_scrollH, (XtPointer) globalRef);

            XtVaSetValues(hsb, XmNhighlightThickness, 0, NULL);
        }
        {
            /**
             * Fix for 4033837 - ScrollPane doesn't generate mouse, focus, key events
             * If ScrollPane created with ALWAYS or AS_NEEDED scrollbars policy then
             * the upper widget is ClipWindow. We should install callbacks on it to
             * receive event notifications.
             */
            Widget clip = XtNameToWidget(sdata->widget, "*ClipWindow");
            if (clip != NULL) {
                // To be able to request focus on Manager by call
                // XmProcessTraversal(, XmTRAVERSE_CURRENT) we need to make
                // it return XmCONTROL_NAVIGABLE from widgetNavigable callback.
                // Default implementation returns DESCENDANTS_TAB_NAVIGABLE
                // which doesn't allow this.
                if (!clipCallbackInitialized) {
                    XmBaseClassExt *er;
                    clipCallbackInitialized = True;
                    er = _XmGetBaseClassExtPtr(XtClass(clip), XmQmotif);
                    oldClipNavigable = (*er)->widgetNavigable;
                    (*er)->widgetNavigable = MyClipNavigable;
                }
                awt_addWidget(clip, sdata->widget, globalRef, java_awt_AWTEvent_MOUSE_EVENT_MASK |
                              java_awt_AWTEvent_MOUSE_MOTION_EVENT_MASK | java_awt_AWTEvent_KEY_EVENT_MASK);
            }
        }
        {
            /**
             * Fix for 4033837 - ScrollPane with ALWAYS doesn't have scrollbars visible
             * It seems to be the bug in Motif, the workaround is to add empty child.
             * User child will replace it when needed. This doesn't work if child had been
             * removed.
             */
            if (sbDisplay == java_awt_ScrollPane_SCROLLBARS_ALWAYS) {
                Widget darea = NULL;
                argc = 0;
                XtSetArg(args[argc], XmNwidth, 1);
                argc++;
                XtSetArg(args[argc], XmNheight, 1);
                argc++;
                XtSetArg(args[argc], XmNmarginWidth, 0);
                argc++;
                XtSetArg(args[argc], XmNmarginHeight, 0);
                argc++;
                XtSetArg(args[argc], XmNspacing, 0);
                argc++;
                XtSetArg(args[argc], XmNresizePolicy, XmRESIZE_NONE);
                argc++;
                darea = XmCreateDrawingArea(sdata->widget, "null_child", args, argc);

                XmScrolledWindowSetAreas(sdata->widget, NULL, NULL, darea);
                XtSetMappedWhenManaged(darea, False);
                XtManageChild(darea);
            }
        }

    }

    XtSetMappedWhenManaged(sdata->widget, False);
    XtManageChild(sdata->widget);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MScrollPanePeer
 * Method:    pSetScrollChild
 * Signature: (Lsun/awt/motif/MComponentPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MScrollPanePeer_pSetScrollChild
  (JNIEnv *env, jobject this, jobject child)
{
    struct ComponentData *cdata;
    struct ComponentData *sdata;
    jobject target;

    AWT_LOCK();

    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    if (JNU_IsNull(env, child) || JNU_IsNull(env, target)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();

        return;
    }
    cdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,child,mComponentPeerIDs.pData);
    sdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (sdata == NULL || cdata == NULL || sdata->widget == NULL || cdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();

        return;
    }
    if ((*env)->GetIntField(env, target, scrollPaneIDs.scrollbarDisplayPolicy)
        == java_awt_ScrollPane_SCROLLBARS_NEVER) {
        /* Do Nothing */
    } else {
        XmScrolledWindowSetAreas(sdata->widget, NULL, NULL, cdata->widget);
        /*
          XtInsertEventHandler(cdata->widget, StructureNotifyMask, FALSE,
          child_event_handler, sdata->widget, XtListHead);
        */
    }

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MScrollPanePeer
 * Method:    pSetIncrement
 * Signature: (III)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MScrollPanePeer_pSetIncrement
  (JNIEnv *env, jobject this, jint orient, jint incrType, jint incr)
{
    struct ComponentData *sdata;
    Widget scrollbar = NULL;

    AWT_LOCK();

    sdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);

    if (sdata == NULL || sdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if (!XtIsSubclass(sdata->widget, xmScrolledWindowWidgetClass)) {
        AWT_UNLOCK();
        return;
    }
    if (orient == java_awt_Adjustable_VERTICAL) {
        XtVaGetValues(sdata->widget,
                      XmNverticalScrollBar, &scrollbar,
                      NULL);
    } else {
        XtVaGetValues(sdata->widget,
                      XmNhorizontalScrollBar, &scrollbar,
                      NULL);
    }

    if (scrollbar != NULL) {
        if (incrType == sun_awt_motif_MScrollPanePeer_UNIT_INCREMENT) {
            XtVaSetValues(scrollbar,
                          XmNincrement, (XtArgVal) incr,
                          NULL);

        } else {
            /* BLOCK_INCREMENT */
            XtVaSetValues(scrollbar,
                          XmNpageIncrement, (XtArgVal) incr,
                          NULL);
        }
    }
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MScrollPanePeer
 * Method:    pGetScrollbarSpace
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MScrollPanePeer_pGetScrollbarSpace
  (JNIEnv *env, jobject this, jint orient)
{
    struct ComponentData *sdata;
    Widget scrollbar;
    Dimension thickness = 0;
    Dimension space = 0;
    Dimension highlight = 0;

    AWT_LOCK();

    sdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (sdata == NULL || sdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return 0;
    }
    if (orient == java_awt_Adjustable_VERTICAL) {
        XtVaGetValues(sdata->widget,
                      XmNverticalScrollBar, &scrollbar,
                      XmNspacing, &space,
                      NULL);
        XtVaGetValues(scrollbar,
                      XmNwidth, &thickness,
                      XmNhighlightThickness, &highlight,
                      NULL);
    } else {
        XtVaGetValues(sdata->widget,
                      XmNhorizontalScrollBar, &scrollbar,
                      XmNspacing, &space,
                      NULL);
        XtVaGetValues(scrollbar,
                      XmNheight, &thickness,
                      XmNhighlightThickness, &highlight,
                      NULL);
    }

    AWT_UNLOCK();
    return (jint) (thickness + space + 2 * highlight);
}

/*
 * Class:     sun_awt_motif_MScrollPanePeer
 * Method:    pGetBlockIncrement
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MScrollPanePeer_pGetBlockIncrement
  (JNIEnv *env, jobject this, jint orient)
{
    int32_t pageIncr = 0;
    struct ComponentData *sdata;
    Widget scrollbar;

    AWT_LOCK();

    sdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (sdata == NULL || sdata->widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return 0;
    }
    if (orient == java_awt_Adjustable_VERTICAL) {

        XtVaGetValues(sdata->widget,
                      XmNverticalScrollBar, &scrollbar,
                      NULL);
        XtVaGetValues(scrollbar,
                      XmNpageIncrement, &pageIncr,
                      NULL);
    } else {

        XtVaGetValues(sdata->widget,
                      XmNhorizontalScrollBar, &scrollbar,
                      NULL);
        XtVaGetValues(scrollbar,
                      XmNpageIncrement, &pageIncr,
                      NULL);
    }

    AWT_UNLOCK();
    return (jint) (pageIncr);
}

/*
 * Class:     sun_awt_motif_MScrollPanePeer
 * Method:    pInsets
 * Signature: (IIII)Ljava/awt/Insets;
 */
JNIEXPORT jobject JNICALL Java_sun_awt_motif_MScrollPanePeer_pInsets
  (JNIEnv *env, jobject this, jint width, jint height, jint childWidth, jint childHeight)
{
    struct ComponentData *sdata;
    jobject target;
    jobject insets = NULL;
    Widget hsb, vsb;
    Dimension hsbThickness, hsbHighlight, hsbSpace = 0,
              vsbThickness, vsbHighlight, vsbSpace = 0,
              space, border, shadow, hMargin, vMargin;
    unsigned char placement;
    Boolean hsbVisible, vsbVisible;
    jint sbDisplay;
    int32_t top, left, right, bottom;
    jclass clazz;
    jmethodID mid;

    AWT_LOCK();

    sdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    if (JNU_IsNull(env, target) || sdata == NULL || sdata->widget == NULL)
    {
        JNU_ThrowNullPointerException(env, "sdata is NULL");
        AWT_UNLOCK();
        return 0;
    }
    sbDisplay =
      (*env)->GetIntField(env, target, scrollPaneIDs.scrollbarDisplayPolicy);

    /* REMIND: investigate caching these values rather than querying for
     * them each time.
     */

    if (sbDisplay == java_awt_ScrollPane_SCROLLBARS_NEVER) {
        XtVaGetValues(sdata->widget,
                      XmNshadowThickness, &shadow,
                      NULL);
        space = border = hMargin = vMargin = 0;

    } else {
        XtVaGetValues(sdata->widget,
                      XmNverticalScrollBar, &vsb,
                      XmNhorizontalScrollBar, &hsb,
                      XmNscrollBarPlacement, &placement,
                      XmNspacing, &space,
                      XmNshadowThickness, &shadow,
                      XmNscrolledWindowMarginHeight, &vMargin,
                      XmNscrolledWindowMarginWidth, &hMargin,
                      XmNborderWidth, &border,
                      NULL);

        XtVaGetValues(vsb,
                      XmNwidth, &vsbThickness,
                      XmNhighlightThickness, &vsbHighlight,
                      NULL);

        XtVaGetValues(hsb,
                      XmNheight, &hsbThickness,
                      XmNhighlightThickness, &hsbHighlight,
                      NULL);

        hsbSpace = hsbThickness + space + hsbHighlight;
        vsbSpace = vsbThickness + space + vsbHighlight;

/*
  XtVaGetValues(clip,
  XmNwidth, &clipw, XmNheight, &cliph,
  XmNx, &clipx, XmNy, &clipy,
  NULL);
  printf("insets: spacing=%d shadow=%d swMarginH=%d swMarginW=%d border=%d ; \
  vsb=%d vsbHL=%d ; hsb=%d hsbHL=%d ; %dx%d ->clip=%d,%d %dx%d\n",
  space, shadow, vMargin, hMargin, border,
  vsbThickness, vsbHighlight, hsbThickness, hsbHighlight,
  w, h, clipx, clipy, clipw, cliph);
*/
    }

    /* We unfortunately have to use the size parameters to determine
     * whether or not "as needed" scrollbars are currently present or
     * not because we can't necessarily rely on getting valid geometry
     * values straight from the Motif widgets until they are mapped. :(
     */
    switch (sbDisplay) {
        case java_awt_ScrollPane_SCROLLBARS_NEVER:
            vsbVisible = hsbVisible = FALSE;
            break;

        case java_awt_ScrollPane_SCROLLBARS_ALWAYS:
            vsbVisible = hsbVisible = TRUE;
            break;

        case java_awt_ScrollPane_SCROLLBARS_AS_NEEDED:
        default:
            vsbVisible = hsbVisible = FALSE;
            if (childWidth > width - 2 * shadow) {
                hsbVisible = TRUE;
            }
            if (childHeight > height - 2 * shadow) {
                vsbVisible = TRUE;
            }
            if (!hsbVisible && vsbVisible && childWidth > width - 2 * shadow - vsbSpace) {
                hsbVisible = TRUE;
            } else if (!vsbVisible && hsbVisible && childHeight > height - 2 * shadow - hsbSpace) {
                vsbVisible = TRUE;
            }
    }

    top = bottom = shadow + vMargin;
    left = right = shadow + hMargin;

    if (sbDisplay != java_awt_ScrollPane_SCROLLBARS_NEVER) {
        switch (placement) {
            case XmBOTTOM_RIGHT:
                bottom += (hsbVisible ? hsbSpace : (vsbVisible ? vsbHighlight : 0));
                right += (vsbVisible ? vsbSpace : (hsbVisible ? hsbHighlight : 0));
                top += (vsbVisible ? vsbHighlight : 0);
                left += (hsbVisible ? hsbHighlight : 0);
                break;

            case XmBOTTOM_LEFT:
                bottom += (hsbVisible ? hsbSpace : (vsbVisible ? vsbHighlight : 0));
                left += (vsbVisible ? hsbSpace : (hsbVisible ? hsbHighlight : 0));
                top += (vsbVisible ? vsbHighlight : 0);
                right += (hsbVisible ? hsbHighlight : 0);
                break;

            case XmTOP_RIGHT:
                top += (hsbVisible ? hsbSpace : (vsbVisible ? vsbHighlight : 0));
                right += (vsbVisible ? vsbSpace : (hsbVisible ? hsbHighlight : 0));
                bottom += (vsbVisible ? vsbHighlight : 0);
                left += (hsbVisible ? hsbHighlight : 0);
                break;

            case XmTOP_LEFT:
                top += (hsbVisible ? hsbSpace : (vsbVisible ? vsbHighlight : 0));
                left += (vsbVisible ? vsbSpace : (hsbVisible ? hsbHighlight : 0));
                bottom += (vsbVisible ? vsbHighlight : 0);
                right += (hsbVisible ? hsbHighlight : 0);
        }
    }
    /* Deadlock prevention:
     * don't hold the toolkit lock while invoking constructor.
     */
    AWT_UNLOCK();

    clazz = (*env)->FindClass(env, "java/awt/Insets");
    mid = (*env)->GetMethodID(env, clazz, "<init>", "(IIII)V");
    if (mid != NULL) {
        insets = (*env)->NewObject(env, clazz, mid,
                                   (jint) top,
                                   (jint) left,
                                   (jint) bottom,
                                   (jint) right);

    }
    /* This should catch both method not found and error exceptions */
    if ((*env)->ExceptionOccurred(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }
    if (JNU_IsNull(env, insets)) {
        JNU_ThrowNullPointerException(env, "NullPointerException: insets constructor failed");
    }
    return insets;
}

/*
 * Class:     sun_awt_motif_MScrollPanePeer
 * Method:    setScrollPosition
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MScrollPanePeer_setScrollPosition
  (JNIEnv *env, jobject this, jint x, jint y)
{
    struct ComponentData *sdata;
    jobject target;
    Widget hsb, vsb;
    int32_t size, incr, pIncr;

    AWT_LOCK();

    sdata = (struct ComponentData *)
        JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    if (JNU_IsNull(env, target) || sdata == NULL || sdata->widget == NULL)
    {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    if ((*env)->GetIntField(env, target, scrollPaneIDs.scrollbarDisplayPolicy)
        == java_awt_ScrollPane_SCROLLBARS_NEVER) {
        WidgetList children;
        Cardinal numChildren;

        XtVaGetValues(sdata->widget,
                      XmNchildren, &children,
                      XmNnumChildren, &numChildren,
                      NULL);

        if (numChildren < 1) {
            JNU_ThrowNullPointerException(env, "NullPointerException");
            AWT_UNLOCK();
            return;
        }
        XtMoveWidget(children[0], (Position) -x, (Position) -y);
    } else {
        int32_t sb_min = 0;
        int32_t sb_max = 0;
        XtVaGetValues(sdata->widget,
                      XmNhorizontalScrollBar, &hsb,
                      XmNverticalScrollBar, &vsb,
                      NULL);

        if (vsb) {
            XtVaGetValues(vsb,
                          XmNincrement, &incr,
                          XmNpageIncrement, &pIncr,
                          XmNsliderSize, &size,
                          XmNminimum, &sb_min,
                          XmNmaximum, &sb_max,
                          NULL);
            /* Bug 4208972, 4275934 : Do range checking for scroll bar value. */
            if (y < sb_min)
                y = sb_min;
            if (y > (sb_max - size))
                y = sb_max - size;
            XmScrollBarSetValues(vsb, (int32_t) y, size, incr, pIncr, TRUE);
        }
        if (hsb) {
            XtVaGetValues(hsb,
                          XmNincrement, &incr,
                          XmNpageIncrement, &pIncr,
                          XmNsliderSize, &size,
                          XmNminimum, &sb_min,
                          XmNmaximum, &sb_max,
                          NULL);
            /* Bug 4208972, 4275934 : Do range checking for scroll bar value. */
            if (x < sb_min)
                x = sb_min;
            if (x > (sb_max - size))
                x = sb_max - size;
            XmScrollBarSetValues(hsb, (int32_t) x, size, incr, pIncr, TRUE);
        }
    }
    AWT_FLUSH_UNLOCK();
}


/*
 * Class:     sun_awt_motif_MScrollPanePeer
 * Method:    pGetShadow
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_awt_motif_MScrollPanePeer_pGetShadow(
                       JNIEnv *env, jobject this) {
    struct ComponentData *sdata;
    jobject target;
    Dimension shadow=0 ;

    AWT_LOCK() ;
    sdata = (struct ComponentData *)
    (*env)->GetLongField(env,this,mComponentPeerIDs.pData);
    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);

    if (JNU_IsNull(env, target) || sdata == NULL || sdata->widget == NULL)
    {
        JNU_ThrowNullPointerException(env, "sdata is NULL");
        AWT_UNLOCK();
        return 0;
    }

    XtVaGetValues(sdata->widget,
        XmNshadowThickness,
        &shadow,
        NULL);

    AWT_UNLOCK() ;

    return((jint)shadow) ;
}

/*
 * Class:     sun_awt_motif_MScrollPanePeer
 * Method:    setTypedValue
 * Signature: (Ljava/awt/ScrollPaneAdjustable;II)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_motif_MScrollPanePeer_setTypedValue(JNIEnv *env, jobject peer, jobject adjustable, jint value, jint type)
{
    static jmethodID setTypedValueMID = 0;
    if (setTypedValueMID == NULL) {
        jclass clazz = (*env)->FindClass(env, "java/awt/ScrollPaneAdjustable");
        if ((*env)->ExceptionOccurred(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
            return;
        }
        setTypedValueMID = (*env)->GetMethodID(env, clazz, "setTypedValue", "(II)V");
        (*env)->DeleteLocalRef(env, clazz);

        DASSERT(setTypedValueMID != NULL);
    }
    (*env)->CallVoidMethod(env, adjustable, setTypedValueMID, value, type);
}
