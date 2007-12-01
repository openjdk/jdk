/*
 * Copyright 1995-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "java_awt_List.h"
#include "java_awt_AWTEvent.h"
#include "sun_awt_motif_MListPeer.h"
#include "sun_awt_motif_MComponentPeer.h"
#include "java_awt_event_MouseWheelEvent.h"
#include "canvas.h"

#include "awt_Component.h"

#include "multi_font.h"
#include <jni.h>
#include <jni_util.h>

extern struct MComponentPeerIDs mComponentPeerIDs;
extern struct ComponentIDs componentIDs;
extern AwtGraphicsConfigDataPtr
    copyGraphicsConfigToPeer(JNIEnv *env, jobject this);


/*
 * client_data = MListPeer instance
 */
static void
Slist_callback(Widget w, XtPointer client_data, XtPointer call_data)
{
    JNIEnv *env = (JNIEnv *)JNU_GetEnv(jvm, JNI_VERSION_1_2);
    XmListCallbackStruct *cbs = (XmListCallbackStruct *) call_data;

    switch (cbs->reason) {
        case XmCR_DEFAULT_ACTION: {
            ConvertEventTimeAndModifiers converted;

            awt_util_convertEventTimeAndModifiers(cbs->event, &converted);

            if (cbs->event->type == KeyPress) {
                /* When Default action comes from keyboard, no notification
                 * is given by motif that a selection has been made, even
                 * though, internally, the item will now be selected regardless
                 * of whether or not it was previously selected.  ( on mouse
                 * generated DEFAULT ACTIONS the XmCR_BROWSE_SELECT is
                 * generated first ).
                 */
                JNU_CallMethodByName(env, NULL, (jobject) client_data
                                     ,"handleListChanged"
                                     ,"(I)V"
                                     ,(cbs->item_position - 1));
                if ((*env)->ExceptionOccurred(env)) {
                    (*env)->ExceptionDescribe(env);
                    (*env)->ExceptionClear(env);
                }
            }

            JNU_CallMethodByName(env, NULL, (jobject) client_data
                                 ,"action"
                                 ,"(IJI)V"
                                 ,(cbs->item_position - 1)
                                 ,converted.when
                                 ,converted.modifiers);
            if ((*env)->ExceptionOccurred(env)) {
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
            }
            break;
        }
        case XmCR_BROWSE_SELECT:
            JNU_CallMethodByName(env, NULL, (jobject) client_data
                                 ,"handleListChanged"
                                 ,"(I)V"
                                 ,(cbs->item_position - 1));
            if ((*env)->ExceptionOccurred(env)) {
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
            }
            break;

        case XmCR_MULTIPLE_SELECT:
            JNU_CallMethodByName(env, NULL, (jobject) client_data
                                 ,"handleListChanged"
                                 ,"(I)V"
                                 ,(cbs->item_position - 1));
            if ((*env)->ExceptionOccurred(env)) {
                (*env)->ExceptionDescribe(env);
                (*env)->ExceptionClear(env);
            }
            break;

        default:
            break;
    }
}

/*
 * Class:     sun_awt_motif_MListPeer
 * Method:    create
 * Signature: (Lsun/awt/motif/MComponentPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MListPeer_create
  (JNIEnv *env, jobject this, jobject parent)
{
    Cardinal argc;
#define MAX_ARGC 40
    Arg args[MAX_ARGC];
    struct ComponentData *wdata;
    struct ListData *sdata;
    Pixel bg;
    jobject globalRef = awtJNI_CreateAndSetGlobalRef(env, this);
    AwtGraphicsConfigDataPtr adata;

    AWT_LOCK();

    adata = copyGraphicsConfigToPeer(env, this);

    if (JNU_IsNull(env, parent)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();

        return;
    }
    wdata = (struct ComponentData *) JNU_GetLongFieldAsPtr(env,parent,mComponentPeerIDs.pData);

    if (wdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    sdata = (struct ListData *) calloc(1, sizeof(struct ListData));

    JNU_SetLongFieldFromPtr(env,this,mComponentPeerIDs.pData,sdata);
    if (sdata == NULL) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();
        return;
    }
    XtVaGetValues(wdata->widget, XmNbackground, &bg, NULL);
    argc = 0;
    XtSetArg(args[argc], XmNrecomputeSize, False);
    argc++;
    XtSetArg(args[argc], XmNbackground, bg);
    argc++;
    XtSetArg(args[argc], XmNlistSizePolicy, XmCONSTANT);
    argc++;
    XtSetArg(args[argc], XmNx, 0);
    argc++;
    XtSetArg(args[argc], XmNy, 0);
    argc++;
    XtSetArg(args[argc], XmNmarginTop, 0);
    argc++;
    XtSetArg(args[argc], XmNmarginBottom, 0);
    argc++;
    XtSetArg(args[argc], XmNmarginLeft, 0);
    argc++;
    XtSetArg(args[argc], XmNmarginRight, 0);
    argc++;
    XtSetArg(args[argc], XmNmarginHeight, 0);
    argc++;
    XtSetArg(args[argc], XmNmarginWidth, 0);
    argc++;
    XtSetArg(args[argc], XmNlistMarginHeight, 0);
    argc++;
    XtSetArg(args[argc], XmNlistMarginWidth, 0);
    argc++;
    XtSetArg(args[argc], XmNscrolledWindowMarginWidth, 0);
    argc++;
    XtSetArg(args[argc], XmNscrolledWindowMarginHeight, 0);
    argc++;
    XtSetArg(args[argc], XmNuserData, (XtPointer) globalRef);
    argc++;
    XtSetArg (args[argc], XmNscreen,
              ScreenOfDisplay(awt_display,
                              adata->awt_visInfo.screen));
    argc++;

    DASSERT(!(argc > MAX_ARGC));
    sdata->list = XmCreateScrolledList(wdata->widget,
                                       "slist",
                                       args,
                                       argc);

    sdata->comp.widget = XtParent(sdata->list);
    XtSetMappedWhenManaged(sdata->comp.widget, False);
    XtAddCallback(sdata->list,
                  XmNdefaultActionCallback,
                  Slist_callback,
                  (XtPointer) globalRef);
    XtAddEventHandler(sdata->list, FocusChangeMask,
                      True, awt_canvas_event_handler, globalRef);

    awt_addWidget(sdata->list, sdata->comp.widget, globalRef,
                  java_awt_AWTEvent_KEY_EVENT_MASK |
                  java_awt_AWTEvent_MOUSE_EVENT_MASK |
                  java_awt_AWTEvent_MOUSE_MOTION_EVENT_MASK);

    XtManageChild(sdata->list);
    XtManageChild(sdata->comp.widget);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MListPeer
 * Method:    setMultipleSelections
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MListPeer_setMultipleSelections
  (JNIEnv *env, jobject this, jboolean v)
{
    struct ListData *sdata;
    jobject globalRef;
    int32_t selPos;
    Boolean selected;

    AWT_LOCK();

    sdata = (struct ListData *)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (sdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    globalRef = (jobject)
        JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.jniGlobalRef);
    if (v == JNI_FALSE) {
        XtVaSetValues(sdata->list,
                      XmNselectionPolicy, XmBROWSE_SELECT,
                      NULL);
        XtRemoveCallback(sdata->list,
                         XmNmultipleSelectionCallback,
                         Slist_callback,
                         (XtPointer) globalRef);
        XtAddCallback(sdata->list,
                      XmNbrowseSelectionCallback,
                      Slist_callback,
                      (XtPointer) globalRef);

        // If we change the selection mode from multiple to single
        // we need to decide what the item should be selected:
        // If a selected item has the location cursor, only that
        // item will remain selected.  If no selected item has the
        // location cursor, all items will be deselected.
        selPos = XmListGetKbdItemPos(sdata->list);
        selected = XmListPosSelected(sdata->list, selPos);
        XmListDeselectAllItems(sdata->list);
        if (selected) {
            Java_sun_awt_motif_MListPeer_select(env, this, selPos-1);
        }

    } else {
        XtVaSetValues(sdata->list,
                      XmNselectionPolicy, XmMULTIPLE_SELECT,
                      NULL);
        XtRemoveCallback(sdata->list,
                         XmNbrowseSelectionCallback,
                         Slist_callback,
                         (XtPointer) globalRef);
        XtAddCallback(sdata->list,
                      XmNmultipleSelectionCallback,
                      Slist_callback,
                      (XtPointer) globalRef);
    }
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MListPeer
 * Method:    setBackground
 * Signature: (Ljava/awt/Color;)V
 */

JNIEXPORT void JNICALL Java_sun_awt_motif_MListPeer_setBackground
  (JNIEnv *env, jobject this, jobject c)
{
    struct ListData *ldata;
    Pixel color;

    if (JNU_IsNull(env, c)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        return;
    }
    AWT_LOCK();
    ldata = (struct ListData *)
        JNU_GetLongFieldAsPtr(env,this, mComponentPeerIDs.pData);
    if (ldata == NULL || ldata->list == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    color = awtJNI_GetColor(env, c);
    XtVaSetValues(ldata->list,
                  XmNbackground, color,
                  NULL);
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MListPeer
 * Method:    isSelected
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_motif_MListPeer_isSelected
  (JNIEnv *env, jobject this, jint pos)
{
    struct ListData *sdata;

    AWT_LOCK();

    sdata = (struct ListData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (sdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return JNI_FALSE;
    }
    pos++;
    if (XmListPosSelected(sdata->list, pos) == True) {
        AWT_UNLOCK();
        return JNI_TRUE;
    } else {
        AWT_UNLOCK();
        return JNI_FALSE;
    }
}

/*
 * Class:     sun_awt_motif_MListPeer
 * Method:    addItem
 * Signature: (Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MListPeer_addItem
  (JNIEnv *env, jobject this, jstring item, jint index)
{
    XmString im;
    struct ListData *sdata;
    jobject font;

    /*
     * Note:
     * There used to be code in this function to fix:
     *  4067355 size of listbox depends on when pack() is called (solaris)
     * The fix (for jdk1.1.7) involved unmapping the List widget before the add
     * is done and resizing/remapping it after the add. This causes significant
     * performance degradation if addItem() is called a lot. A bug was filed
     * on this performance problem: 4117288
     * The fix was backed out after testing that:
     *  - the problem reported in 4067355 was no longer reproducible
     *  - the performance problem is gone
     */

    AWT_LOCK();
    if (JNU_IsNull(env, item)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    sdata = (struct ListData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (sdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    font = awtJNI_GetFont(env, this);

    if (awtJNI_IsMultiFont(env, font)) {
        im = awtJNI_MakeMultiFontString(env, item, font);
    } else {
        char *temp;

        temp = (char *) JNU_GetStringPlatformChars(env, item, NULL);
        im = XmStringCreateLocalized(temp);
        JNU_ReleaseStringPlatformChars(env, item, (const char *)temp);
    }

    /* motif uses 1-based indeces for the list operations with 0 */
    /* referring to the last item on the list. Thus if index is -1 */
    /* then we'll get the right effect of adding to the end of the */
    /* list. */
    index++;

    XmListAddItemUnselected(sdata->list, im, index);
    XmStringFree(im);

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MListPeer
 * Method:    delItems
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MListPeer_delItems
  (JNIEnv *env, jobject this, jint start, jint end)
{
    struct ListData *sdata;
    Boolean was_mapped;
    jobject target;
    Position width, height;
    int32_t itemCount;

    AWT_LOCK();
    target = (*env)->GetObjectField(env, this, mComponentPeerIDs.target);
    if (JNU_IsNull(env, target)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    sdata = (struct ListData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (sdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    /* [jk] catch bogus indexes (Sun bug) */
    XtVaGetValues(sdata->list, XmNitemCount, &itemCount, NULL);
    if (itemCount == 0) {
        AWT_UNLOCK();
        return;
    }
    if (start > itemCount) {
        start = itemCount;
    }
    if (end > itemCount) {
        end = itemCount;
    }
    start++;
    end++;

    XtVaGetValues(sdata->comp.widget, XmNmappedWhenManaged, &was_mapped, NULL);

    /* If it was visible, then make it invisible while we update */
    if (was_mapped) {
      XtSetMappedWhenManaged(sdata->comp.widget, False);
    }

    if (start == end) {
        XmListDeletePos(sdata->list, start);
    } else {
        XmListDeleteItemsPos(sdata->list, end - start + 1, start);
    }

    width = (*env)->GetIntField(env, target, componentIDs.width);
    height = (*env)->GetIntField(env, target, componentIDs.height);
    XtVaSetValues(sdata->comp.widget,
                  XmNwidth, (width > 1) ? width-1 : 1,
                  XmNheight, (height > 1) ? height-1 : 1,
                  NULL);
    XtVaSetValues(sdata->comp.widget,
                  XmNwidth, (width > 0) ? width : 1,
                  XmNheight, (height > 0) ? height : 1,
                  NULL);
    /* If it was visible, then make it visible again once updated */
    if (was_mapped) {
        XtSetMappedWhenManaged(sdata->comp.widget, True);
    }


    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MListPeer
 * Method:    pSelect
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MListPeer_select
  (JNIEnv *env, jobject this, jint pos)
{
    struct ListData *sdata;

    AWT_LOCK();
    sdata = (struct ListData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (sdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    pos++;
    XmListSelectPos(sdata->list, pos, False);
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MListPeer
 * Method:    pDeselect
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MListPeer_deselect
  (JNIEnv *env, jobject this, jint pos)
{
    struct ListData *sdata;

    AWT_LOCK();
    sdata = (struct ListData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (sdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    pos++;
    XmListDeselectPos(sdata->list, pos);
    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MListPeer
 * Method:    makeVisible
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MListPeer_makeVisible
  (JNIEnv *env, jobject this, jint pos)
{
    int32_t top, visible;
    struct ListData *sdata;

    AWT_LOCK();
    sdata = (struct ListData *) JNU_GetLongFieldAsPtr(env,this,mComponentPeerIDs.pData);
    if (sdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    XtVaGetValues(sdata->list,
                  XmNtopItemPosition, &top,
                  XmNvisibleItemCount, &visible,
                  NULL);
    pos++;
    if (pos < top) {
        XmListSetPos(sdata->list, pos);
    } else {
        XmListSetBottomPos(sdata->list, pos);
    }

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MListPeer
 * Method:    nativeHandleMouseWheel
 * Signature: (III)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MListPeer_nativeHandleMouseWheel
  (JNIEnv *env, jobject this, jint scrollType, jint scrollAmt, jint wheelAmt)
{
    struct ListData *ldata;
    Widget list = NULL;
    Widget scroll = NULL;

    AWT_LOCK();
    ldata = (struct ListData *)
      JNU_GetLongFieldAsPtr(env, this, mComponentPeerIDs.pData);
    if (ldata == NULL || ldata->comp.widget == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }
    // get the List widget
    list = ldata->list;
    if (list == NULL) {
        AWT_UNLOCK();
        return;
    }

    // get the ScrolledWindow
    scroll = XtParent(list);
    if (scroll == NULL) {
        AWT_UNLOCK();
        return;
    }

    awt_util_do_wheel_scroll(scroll, scrollType, scrollAmt, wheelAmt);
    AWT_UNLOCK();
}
